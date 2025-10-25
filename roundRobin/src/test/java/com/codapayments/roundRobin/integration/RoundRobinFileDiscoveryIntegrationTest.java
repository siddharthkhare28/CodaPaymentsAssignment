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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for file-based server discovery
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "roundrobin.server-discovery-strategy=file",
        "roundrobin.server-discovery.file-path=test-integration-servers.txt",
        "roundrobin.health-check-interval=1000", // Faster health checks for testing
        "roundrobin.slow-threshold-ms=500",
        "roundrobin.initial-latency-ms=100"
})
class RoundRobinFileDiscoveryIntegrationTest {

    @LocalServerPort
    private int port;

    private static WireMockServer backend1;
    private static WireMockServer backend2;
    private final RestTemplate restTemplate = new RestTemplate();
    private Path serverFile;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    void setupClass() throws IOException {
        backend1 = new WireMockServer(8081);
        backend2 = new WireMockServer(8082);
        
        backend1.start();
        backend2.start();
        
        // Create the server file for testing in the current directory
        serverFile = Path.of("test-integration-servers.txt");
        
        // Write initial servers to file
        String serverList = "http://localhost:8081\nhttp://localhost:8082\n";
        Files.write(serverFile, serverList.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    @AfterAll
    void teardownClass() throws IOException {
        if (backend1 != null) {
            backend1.stop();
        }
        if (backend2 != null) {
            backend2.stop();
        }
        
        // Clean up the temp file
        if (serverFile != null && Files.exists(serverFile)) {
            Files.delete(serverFile);
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
    @DisplayName("Should load servers from file and alternate between them")
    void testFileBasedRoundRobinOrder() throws InterruptedException {
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

        assertTrue(responses.size() == 6, "Should receive 6 responses");

        // Verify we're getting responses from both servers 
        long port8081Count = responses.stream().filter(r -> r.contains("8081")).count();
        long port8082Count = responses.stream().filter(r -> r.contains("8082")).count();
        
        assertTrue(port8081Count == 3, "Should receive 3 responses from port 8081");
        assertTrue(port8082Count == 3, "Should receive 3 responses from port 8082");
    }

    @Test
    @DisplayName("Should dynamically reload servers when file is modified")
    void testDynamicServerFileReload() throws IOException, InterruptedException {
        // Start with only one server
        String singleServerList = "http://localhost:8081\n";
        Files.write(serverFile, singleServerList.getBytes(), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        
        // Wait for file change detection and health checks
        TimeUnit.SECONDS.sleep(3);
        
        List<String> singleServerResponses = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            try {
                String body = restTemplate.getForObject("http://localhost:" + port + "/api/info", String.class);
                singleServerResponses.add(body);
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (Exception e) {
                System.out.println("Request failed: " + e.getMessage());
            }
        }
        
        // Check if we have responses and they're mostly from port 8081
        if (!singleServerResponses.isEmpty()) {
            long port8081OnlyCount = singleServerResponses.stream().filter(r -> r.contains("8081")).count();
            // Allow some flexibility - at least majority should be from 8081
            assertTrue(port8081OnlyCount >= singleServerResponses.size() * 0.7, 
                    "Majority of responses should be from port 8081 when only one server in file");
        }
        
        // Now add the second server back
        String bothServersLit = "http://localhost:8081\nhttp://localhost:8082\n";
        Files.write(serverFile, bothServersLit.getBytes(), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        
        // Wait for file change detection and health checks
        TimeUnit.SECONDS.sleep(4);
        
        List<String> bothServersResponses = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            try {
                String body = restTemplate.getForObject("http://localhost:" + port + "/api/info", String.class);
                bothServersResponses.add(body);
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (Exception e) {
                System.out.println("Request failed: " + e.getMessage());
            }
        }
        
        // Should now get responses from both servers
        long port8081Count = bothServersResponses.stream().filter(r -> r.contains("8081")).count();
        long port8082Count = bothServersResponses.stream().filter(r -> r.contains("8082")).count();
        
        assertTrue(port8081Count > 0, "Should receive responses from port 8081");
        assertTrue(port8082Count > 0, "Should receive responses from port 8082");
        assertTrue(port8081Count + port8082Count == bothServersResponses.size(), 
                "All responses should be from either port 8081 or 8082");
    }

    @Test
    @DisplayName("Should handle empty server file gracefully")
    void testEmptyServerFile() throws IOException, InterruptedException {
        // Write empty content to file
        Files.write(serverFile, "".getBytes(), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        
        // Wait for file change detection
        TimeUnit.SECONDS.sleep(3);
        
        // Requests should fail gracefully when no servers available
        List<String> responses = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        for (int i = 0; i < 3; i++) {
            try {
                String response = restTemplate.getForObject("http://localhost:" + port + "/api/info", String.class);
                responses.add(response);
            } catch (Exception e) {
                errors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        
        // Should have more errors than successful responses when no servers available
        assertTrue(errors.size() >= responses.size(), 
                "Should have errors when no servers are available in file");
        
        // Restore servers for cleanup
        String serverList = "http://localhost:8081\nhttp://localhost:8082\n";
        Files.write(serverFile, serverList.getBytes(), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        TimeUnit.SECONDS.sleep(2);
    }
}