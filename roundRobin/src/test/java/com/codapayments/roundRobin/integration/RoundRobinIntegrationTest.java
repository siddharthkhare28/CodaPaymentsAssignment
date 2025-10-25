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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "roundrobin.servers[0]=http://localhost:8081",
        "roundrobin.servers[1]=http://localhost:8082",
        "roundrobin.health-check-interval=1000", // Faster health checks for testing
        "roundrobin.slow-threshold-ms=500",
        "roundrobin.initial-latency-ms=100"
})
class RoundRobinIntegrationTest {

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
        
        // Mock health endpoint
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
        
        // Mock API endpoint
        backend1.stubFor(get(urlEqualTo("/api/info"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonResponse1)));
        
        backend2.stubFor(get(urlEqualTo("/api/info"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonResponse2)));

        // Wait for health checks to complete and mark servers as healthy
        TimeUnit.SECONDS.sleep(2);
    }

    @Test
    @DisplayName("Should alternate between backends in round-robin order")
    void testRoundRobinOrder() throws InterruptedException {
        List<String> responses = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < 6; i++) {
            try {
                String body = restTemplate.getForObject("http://localhost:" + port + "/api/info", String.class);
                responses.add(body);
                TimeUnit.MILLISECONDS.sleep(100); // Small delay between requests
            } catch (Exception e) {
                errors.add("Request " + i + " failed: " + e.getMessage());
            }
        }

        assertTrue(responses.size() == 6);

        // Verify we're getting responses from both servers 
        long port8081Count = responses.stream().filter(r -> r.contains("8081")).count();
        long port8082Count = responses.stream().filter(r -> r.contains("8082")).count();
        
        assertTrue(port8081Count == 3, "Should receive responses from port 8081");
        assertTrue(port8082Count == 3, "Should receive responses from port 8082");
    }

    @Test
    @DisplayName("Should skip down backend and continue routing to available one")
    void testBackendFailureHandling() throws InterruptedException {
        // Stop backend2 to simulate failure
        backend2.stop();
        
        // Wait for health check to detect the failure
        TimeUnit.SECONDS.sleep(3);

        List<String> responses = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            try {
                String body = restTemplate.getForObject("http://localhost:" + port + "/api/info", String.class);
                responses.add(body);
                TimeUnit.MILLISECONDS.sleep(200);
            } catch (Exception e) {
                errors.add("Request " + i + " failed: " + e.getMessage());
            }
        }

        long okCount = responses.stream().filter(r -> r.contains("8081")).count();
        
        assertTrue(okCount == 5, 
                "Should either get successful responses or handle failures gracefully");
        
        // Restart backend2 for cleanup
        backend2 = new WireMockServer(8082);
        backend2.start();
        
        // Re-setup mocks for teardown
        backend2.stubFor(get(urlEqualTo("/actuator/health"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"UP\"}")));
    }
}
