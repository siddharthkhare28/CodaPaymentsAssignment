package com.codapayments.roundRobin.service.impl;

import com.codapayments.roundRobin.model.ServerHealth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LeastResponseTimeStrategyTest {

    private LeastResponseTimeStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new LeastResponseTimeStrategy();
    }

    @Test
    void shouldReturnNullWhenNoServersAvailable() {
        // Given
        List<ServerHealth> emptyList = new ArrayList<>();

        // When
        ServerHealth selected = strategy.selectServer(emptyList);

        // Then
        assertNull(selected);
    }

    @Test
    void shouldReturnNullWhenServerListIsNull() {
        // When
        ServerHealth selected = strategy.selectServer(null);

        // Then
        assertNull(selected);
    }

    @Test
    void shouldReturnSingleServerWhenOnlyOneAvailable() {
        // Given
        ServerHealth server = new ServerHealth("http://localhost:8081", 100L);
        List<ServerHealth> servers = List.of(server);

        // When
        ServerHealth selected = strategy.selectServer(servers);

        // Then
        assertEquals(server, selected);
    }

    @Test
    void shouldSelectServerWithLowestResponseTime() {
        // Given
        ServerHealth fastServer = new ServerHealth("http://localhost:8081", 50L);
        ServerHealth mediumServer = new ServerHealth("http://localhost:8082", 100L);
        ServerHealth slowServer = new ServerHealth("http://localhost:8083", 200L);
        
        List<ServerHealth> servers = List.of(slowServer, fastServer, mediumServer); // Deliberately not in order

        // When
        ServerHealth selected = strategy.selectServer(servers);

        // Then
        assertEquals(fastServer, selected);
        assertEquals("http://localhost:8081", selected.getServerUrl());
    }

    @Test
    void shouldSelectFirstServerWhenResponseTimesAreEqual() {
        // Given
        ServerHealth server1 = new ServerHealth("http://localhost:8081", 100L);
        ServerHealth server2 = new ServerHealth("http://localhost:8082", 100L);
        ServerHealth server3 = new ServerHealth("http://localhost:8083", 100L);
        
        List<ServerHealth> servers = List.of(server1, server2, server3);

        // When
        ServerHealth selected = strategy.selectServer(servers);

        // Then
        assertEquals(server1, selected); // Should return first one when times are equal
    }

    @Test
    void shouldAdaptToChangingResponseTimes() {
        // Given
        ServerHealth server1 = new ServerHealth("http://localhost:8081", 100L);
        ServerHealth server2 = new ServerHealth("http://localhost:8082", 200L);
        List<ServerHealth> servers = List.of(server1, server2);

        // When - Initially server1 should be selected (lower response time)
        ServerHealth firstSelection = strategy.selectServer(servers);
        assertEquals(server1, firstSelection);

        // Change response times - now server2 becomes faster
        server1.updateResponseTime(500L); // (100*4 + 500)/5 = 180
        server2.updateResponseTime(50L);  // (200*4 + 50)/5 = 170

        ServerHealth secondSelection = strategy.selectServer(servers);

        // Then - Now server2 should be selected (has lower average response time)
        assertEquals(server2, secondSelection);
    }

    @Test
    void shouldReturnCorrectStrategyName() {
        // When
        String strategyName = strategy.getStrategyName();

        // Then
        assertEquals("Least Response Time", strategyName);
    }

    @Test
    void shouldHandleExtremeResponseTimes() {
        // Given
        ServerHealth veryFastServer = new ServerHealth("http://localhost:8081", 1L);
        ServerHealth verySlowServer = new ServerHealth("http://localhost:8082", Long.MAX_VALUE);
        List<ServerHealth> servers = List.of(verySlowServer, veryFastServer);

        // When
        ServerHealth selected = strategy.selectServer(servers);

        // Then
        assertEquals(veryFastServer, selected);
    }

    @Test
    void shouldConsistentlySelectFastestServer() {
        // Given
        ServerHealth fastServer = new ServerHealth("http://localhost:8081", 10L);
        ServerHealth slowServer = new ServerHealth("http://localhost:8082", 500L);
        List<ServerHealth> servers = List.of(fastServer, slowServer);

        // When - Multiple selections
        for (int i = 0; i < 10; i++) {
            ServerHealth selected = strategy.selectServer(servers);
            // Then - Should always select the fastest one
            assertEquals(fastServer, selected);
        }
    }
}