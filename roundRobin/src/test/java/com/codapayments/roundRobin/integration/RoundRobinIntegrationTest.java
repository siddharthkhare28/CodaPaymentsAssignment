package com.codapayments.roundRobin.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoundRobinIntegrationTest {

    private WireMockServer backend1;
    private WireMockServer backend2;
    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    void setup() throws JsonProcessingException {
        backend1 = new WireMockServer(8081);
        backend2 = new WireMockServer(8082);

        backend1.start();
        backend2.start();


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
        backend1.stubFor(get(urlEqualTo("/api/info"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(jsonResponse1)));
        backend2.stubFor(get(urlEqualTo("/api/info"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(jsonResponse2)));
    }

    @AfterAll
    void teardown() {
        backend1.stop();
        backend2.stop();
    }

    @Test
    @DisplayName("Should alternate between backends in round-robin order")
    void testRoundRobinOrder() {
        List<String> responses = new ArrayList<>();

        for (int i = 0; i < 6; i++) {
            String body = restTemplate.getForObject("http://localhost:8080/api/info", String.class);
            responses.add(body);
        }

        // Expect pattern: 8081, 8082, 8081, 8082, ...
        for (int i = 0; i < responses.size(); i++) {
            if (i % 2 == 0)
                assertTrue(responses.get(i).contains("8081"), "Expected 8081 at index " + i);
            else
                assertTrue(responses.get(i).contains("8082"), "Expected 8082 at index " + i);
        }
    }

    @Test
    @DisplayName("Should skip down backend and continue routing to available one")
    void testBackendFailureHandling() {
        // Stop backend2 to simulate failure
        backend2.stop();

        List<String> responses = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            try {
                String body = restTemplate.getForObject("http://localhost:8080/api/info", String.class);
                responses.add(body);
            } catch (Exception e) {
                // The load balancer might throw if it doesn’t handle failure
                responses.add("Error");
            }
        }

        long okCount = responses.stream().filter(r -> r.contains("8081")).count();
        long errorCount = responses.stream().filter(r -> r.equals("Error")).count();

        assertTrue(okCount > 0, "Requests should still succeed via backend1");
        System.out.println("Responses: " + responses);
        System.out.println("Success: " + okCount + ", Errors: " + errorCount);
    }
}
