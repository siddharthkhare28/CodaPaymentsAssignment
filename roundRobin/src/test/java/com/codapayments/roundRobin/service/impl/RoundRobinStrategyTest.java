package com.codapayments.roundRobin.service.impl;

import com.codapayments.roundRobin.model.ServerHealth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoundRobinStrategyTest {

    private RoundRobinStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new RoundRobinStrategy();
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
    void shouldAlternateBetweenServersInRoundRobinFashion() {
        // Given
        ServerHealth server1 = new ServerHealth("http://localhost:8081", 100L);
        ServerHealth server2 = new ServerHealth("http://localhost:8082", 100L);
        ServerHealth server3 = new ServerHealth("http://localhost:8083", 100L);
        List<ServerHealth> servers = List.of(server1, server2, server3);

        // When & Then
        assertEquals(server1, strategy.selectServer(servers)); // First call
        assertEquals(server2, strategy.selectServer(servers)); // Second call
        assertEquals(server3, strategy.selectServer(servers)); // Third call
        assertEquals(server1, strategy.selectServer(servers)); // Should wrap around
        assertEquals(server2, strategy.selectServer(servers)); // Continue pattern
    }

    @Test
    void shouldHandleCounterOverflow() {
        // Given
        ServerHealth server1 = new ServerHealth("http://localhost:8081", 100L);
        ServerHealth server2 = new ServerHealth("http://localhost:8082", 100L);
        List<ServerHealth> servers = List.of(server1, server2);

        // When - Simulate many calls to potentially overflow counter
        ServerHealth lastSelected = null;
        for (int i = 0; i < 1000; i++) {
            lastSelected = strategy.selectServer(servers);
            assertNotNull(lastSelected);
        }

        // Then - Should still work after many iterations
        assertNotNull(lastSelected);
        assertTrue(servers.contains(lastSelected));
    }

    @Test
    void shouldReturnCorrectStrategyName() {
        // When
        String strategyName = strategy.getStrategyName();

        // Then
        assertEquals("Round Robin", strategyName);
    }

    @Test
    void shouldDistributeRequestsEvenly() {
        // Given
        ServerHealth server1 = new ServerHealth("http://localhost:8081", 100L);
        ServerHealth server2 = new ServerHealth("http://localhost:8082", 100L);
        List<ServerHealth> servers = List.of(server1, server2);

        int server1Count = 0;
        int server2Count = 0;
        int totalRequests = 100;

        // When
        for (int i = 0; i < totalRequests; i++) {
            ServerHealth selected = strategy.selectServer(servers);
            if (selected == server1) {
                server1Count++;
            } else if (selected == server2) {
                server2Count++;
            }
        }

        // Then - Should be evenly distributed
        assertEquals(50, server1Count);
        assertEquals(50, server2Count);
    }
}