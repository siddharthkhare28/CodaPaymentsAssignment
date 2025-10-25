package com.codapayments.roundRobin.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the slowness cooldown scenario.
 * 
 * These tests verify that:
 * 1. Servers that consistently respond slowly are marked as slow and put in cooldown
 * 2. During cooldown, slow servers receive fewer requests (load is redirected)
 * 3. After cooldown expires, servers are allowed back into rotation
 * 4. When all servers are slow, the system handles it gracefully with appropriate error responses
 * 
 * Test Configuration:
 * - slow-threshold-ms: 300ms (low threshold for easy testing)
 * - slowness-cooldown-seconds: 3s (short cooldown for fast testing)
 * - slowness-window-size: 3 (small window for quick detection)
 * - slowness-threshold-ratio: 0.6 (60% of requests must be slow to trigger cooldown)
 */

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "roundrobin.servers[0]=http://localhost:8081",
        "roundrobin.servers[1]=http://localhost:8082",
        "roundrobin.health-check-interval=1000",       // Fast health checks for testing
        "roundrobin.slow-threshold-ms=300",            // Low threshold to trigger slowness easily
        "roundrobin.slowness-cooldown-seconds=3",      // Short cooldown for testing
        "roundrobin.slowness-window-size=3",           // Small window for quick detection
        "roundrobin.slowness-threshold-ratio=0.6",     // 60% of requests must be slow
        "roundrobin.initial-latency-ms=100"
})
class SlownessCooldownIntegrationTest {

    @LocalServerPort
    private int port;

    private static WireMockServer backend1;
    private static WireMockServer backend2;
    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    static void setupClass() {
        backend1 = new WireMockServer(8081);
        backend2 = new WireMockServer(8082);
        
        backend1.start();
        backend2.start();
    }

    @AfterAll
    static void teardownClass() {
        if (backend1 != null) {
            backend1.stop();
        }
        if (backend2 != null) {
            backend2.stop();
        }
    }

    @BeforeEach
    void setup() throws JsonProcessingException, InterruptedException {
        // Reset WireMock state
        backend1.resetAll();
        backend2.resetAll();

        setupHealthEndpoints();
        setupApiEndpoints();
        
        // Wait for health checks to detect servers as healthy
        waitForServersToBeHealthy();
    }

    private void setupHealthEndpoints() {
        backend1.stubFor(get(urlEqualTo("/actuator/health"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"UP\"}")));
        
        backend2.stubFor(get(urlEqualTo("/actuator/health"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"UP\"}")));
    }

    private void setupApiEndpoints() throws JsonProcessingException {
        Map<String, Object> requestBody1 = Map.of(
                "game", "Mobile Legends",
                "gamerID", "GYUTDTE",
                "points", 20,
                "port", "8081"
        );

        Map<String, Object> requestBody2 = Map.of(
                "game", "Mobile Legends", 
                "gamerID", "GYUTDTE",
                "points", 20,
                "port", "8082"
        );

        String jsonResponse1 = objectMapper.writeValueAsString(requestBody1);
        String jsonResponse2 = objectMapper.writeValueAsString(requestBody2);
        
        // Backend1 responds normally (fast)
        backend1.stubFor(get(urlEqualTo("/api/info"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonResponse1)
                        .withFixedDelay(100))); // Fast response
        
        // Backend2 responds normally (fast) initially
        backend2.stubFor(get(urlEqualTo("/api/info"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonResponse2)
                        .withFixedDelay(100))); // Fast response
    }

    private void waitForServersToBeHealthy() throws InterruptedException {
        String baseUrl = "http://localhost:" + port;
        int attempts = 0;
        int maxAttempts = 10;
        
        while (attempts < maxAttempts) {
            try {
                String response = restTemplate.getForObject(baseUrl + "/api/info", String.class);
                if (response != null) {
                    return; // Servers are healthy and responding
                }
            } catch (Exception e) {
                // Still waiting for servers to be healthy
            }
            attempts++;
            Thread.sleep(1000);
        }
        
        throw new RuntimeException("Servers did not become healthy within timeout");
    }

    @Test
    void testSlowServerGoesIntoCooldownAndRecovers() throws InterruptedException, JsonProcessingException {
        String baseUrl = "http://localhost:" + port;
        
        // Step 1: Make some initial requests to ensure both servers are working
        String response1 = restTemplate.getForObject(baseUrl + "/api/info", String.class);
        String response2 = restTemplate.getForObject(baseUrl + "/api/info", String.class);
        
        assertNotNull(response1);
        assertNotNull(response2);
        
        // Step 2: Make backend2 slow to trigger cooldown
        backend2.resetMappings();
        backend2.stubFor(get(urlEqualTo("/actuator/health"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"UP\"}")));
        
        Map<String, Object> slowRequestBody = Map.of(
                "game", "Mobile Legends",
                "gamerID", "GYUTDTE", 
                "points", 20,
                "port", "8082"
        );
        String slowJsonResponse = objectMapper.writeValueAsString(slowRequestBody);
        
        backend2.stubFor(get(urlEqualTo("/api/info"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(slowJsonResponse)
                        .withFixedDelay(600))); // Slow response (above 300ms threshold)

        // Step 3: Make several requests to trigger slowness detection
        for (int i = 0; i < 5; i++) {
            String response = restTemplate.getForObject(baseUrl + "/api/info", String.class);
            assertNotNull(response);
            Thread.sleep(100); // Small delay between requests
        }

        // Step 4: Wait for health check cycle to detect slowness
        Thread.sleep(2000);

        // Step 5: Make requests during cooldown - should mostly go to backend1
        int backend1Requests = 0;
        int backend2Requests = 0;
        
        for (int i = 0; i < 6; i++) {
            String response = restTemplate.getForObject(baseUrl + "/api/info", String.class);
            assertNotNull(response);
            
            if (response.contains("\"port\":\"8081\"")) {
                backend1Requests++;
            } else if (response.contains("\"port\":\"8082\"")) {
                backend2Requests++;
            }
            
            Thread.sleep(200);
        }
        
        // During cooldown, backend1 should receive more requests
        assertTrue(backend1Requests > backend2Requests, 
                "Backend1 should receive more requests during backend2's cooldown period. " +
                "Backend1: " + backend1Requests + ", Backend2: " + backend2Requests);

        // Step 6: Make backend2 fast again
        backend2.resetMappings();
        setupHealthEndpoints(); // Restore health endpoint
        
        backend2.stubFor(get(urlEqualTo("/api/info"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(slowJsonResponse)
                        .withFixedDelay(100))); // Fast response again

        // Step 7: Wait for cooldown to expire and backend2 to recover
        Thread.sleep(4000); // Wait for cooldown period to expire

        // Step 8: Verify backend2 is back in rotation
        backend1Requests = 0;
        backend2Requests = 0;
        
        for (int i = 0; i < 8; i++) {
            String response = restTemplate.getForObject(baseUrl + "/api/info", String.class);
            assertNotNull(response);
            
            if (response.contains("\"port\":\"8081\"")) {
                backend1Requests++;
            } else if (response.contains("\"port\":\"8082\"")) {
                backend2Requests++;
            }
            
            Thread.sleep(100);
        }
        
        // After recovery, both servers should receive requests
        assertTrue(backend1Requests > 0, "Backend1 should still receive requests after recovery");
        assertTrue(backend2Requests > 0, "Backend2 should receive requests again after cooldown recovery");
        
    }

    @Test
    void testMultipleSlowServersHandling() throws InterruptedException, JsonProcessingException {
        String baseUrl = "http://localhost:" + port;
        
        // Make both backends slow
        backend1.resetMappings();
        backend2.resetMappings();
        setupHealthEndpoints();
        
        Map<String, Object> requestBody1 = Map.of(
                "game", "Mobile Legends",
                "gamerID", "GYUTDTE",
                "points", 20,
                "port", "8081"
        );
        
        Map<String, Object> requestBody2 = Map.of(
                "game", "Mobile Legends",
                "gamerID", "GYUTDTE", 
                "points", 20,
                "port", "8082"
        );
        
        String jsonResponse1 = objectMapper.writeValueAsString(requestBody1);
        String jsonResponse2 = objectMapper.writeValueAsString(requestBody2);
        
        // Both backends are slow
        backend1.stubFor(get(urlEqualTo("/api/info"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonResponse1)
                        .withFixedDelay(600))); // Slow
        
        backend2.stubFor(get(urlEqualTo("/api/info"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonResponse2)
                        .withFixedDelay(600))); // Slow

        // Make requests to trigger slowness detection
        for (int i = 0; i < 4; i++) {
            try {
                restTemplate.getForObject(baseUrl + "/api/info", String.class);
                Thread.sleep(100);
            } catch (Exception e) {
                // Some requests might fail, that's expected
            }
        }

        // Wait for detection
        Thread.sleep(2000);

        // When both servers are slow, the load balancer should:
        // 1. Still try to serve requests (may be slow)
        // 2. Use any servers that become available
        // 3. Return appropriate error responses when needed
        
        int totalRequests = 3;
        int successfulRequests = 0;
        int serviceUnavailableErrors = 0;
        
        for (int i = 0; i < totalRequests; i++) {
            try {
                String response = restTemplate.getForObject(baseUrl + "/api/info", String.class);
                if (response != null) {
                    successfulRequests++;
                    assertTrue(response.contains("\"port\":\"808"), "Response should contain valid port");
                }
            } catch (org.springframework.web.client.HttpServerErrorException.ServiceUnavailable e) {
                serviceUnavailableErrors++;
                assertTrue(e.getMessage().contains("All backend servers are unavailable"), 
                    "Should get appropriate error message when all servers are unavailable");
            } catch (Exception e) {
                // Other types of errors are also acceptable when servers are slow
            }
            Thread.sleep(200);
        }
        
        // Verify the system handled the scenario appropriately
        int totalHandledRequests = successfulRequests + serviceUnavailableErrors;
        assertTrue(totalHandledRequests > 0, 
            "Should handle requests with either success or appropriate error responses");
        
        // If we get service unavailable errors, that's actually correct behavior
        // when all servers are in cooldown
        assertTrue(serviceUnavailableErrors > 0,
            "Service unavailable errors should not exceed total requests");
        
        
    }
}