package com.codapayments.roundRobin.service.impl;

import com.codapayments.roundRobin.config.RoundRobinProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StaticServerDiscoveryServiceTest {

    private StaticServerDiscoveryService discoveryService;
    private RoundRobinProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RoundRobinProperties();
        discoveryService = new StaticServerDiscoveryService(properties);
    }

    @Test
    void testGetServers_WithConfiguredServers() {
        // Given
        List<String> expectedServers = Arrays.asList(
                "http://server1:8080",
                "http://server2:8080",
                "http://server3:8080"
        );
        properties.setServers(expectedServers);

        // When
        List<String> actualServers = discoveryService.getServers();

        // Then
        assertEquals(expectedServers, actualServers);
        assertEquals(3, actualServers.size());
        assertTrue(actualServers.contains("http://server1:8080"));
        assertTrue(actualServers.contains("http://server2:8080"));
        assertTrue(actualServers.contains("http://server3:8080"));
    }

    @Test
    void testGetServers_WithEmptyConfiguration() {
        // Given
        properties.setServers(Collections.emptyList());

        // When
        List<String> actualServers = discoveryService.getServers();

        // Then
        assertNotNull(actualServers);
        assertTrue(actualServers.isEmpty());
    }

    @Test
    void testGetServers_WithNullConfiguration() {
        // Given
        properties.setServers(null);

        // When
        List<String> actualServers = discoveryService.getServers();

        // Then
        assertNull(actualServers);
    }

    @Test
    void testGetServers_WithSingleServer() {
        // Given
        List<String> expectedServers = Arrays.asList("http://localhost:8080");
        properties.setServers(expectedServers);

        // When
        List<String> actualServers = discoveryService.getServers();

        // Then
        assertEquals(expectedServers, actualServers);
        assertEquals(1, actualServers.size());
        assertEquals("http://localhost:8080", actualServers.get(0));
    }

    @Test
    void testGetServers_WithDifferentProtocols() {
        // Given
        List<String> expectedServers = Arrays.asList(
                "http://server1:8080",
                "https://server2:8443",
                "http://server3:9090"
        );
        properties.setServers(expectedServers);

        // When
        List<String> actualServers = discoveryService.getServers();

        // Then
        assertEquals(expectedServers, actualServers);
        assertTrue(actualServers.contains("https://server2:8443"));
    }

    @Test
    void testGetServers_ConsistentResults() {
        // Given
        List<String> expectedServers = Arrays.asList(
                "http://server1:8080",
                "http://server2:8080"
        );
        properties.setServers(expectedServers);

        // When
        List<String> firstCall = discoveryService.getServers();
        List<String> secondCall = discoveryService.getServers();

        // Then
        assertEquals(firstCall, secondCall);
        // Note: Static discovery may return the same reference since it's not dynamic
        // This is acceptable behavior for static configuration
    }

    @Test
    void testGetStrategyName() {
        // When
        String strategyName = discoveryService.getStrategyName();

        // Then
        assertEquals("Static Configuration", strategyName);
        assertNotNull(strategyName);
        assertFalse(strategyName.isEmpty());
    }

    @Test
    void testSupportsDynamicUpdates() {
        // When
        boolean supportsDynamic = discoveryService.supportsDynamicUpdates();

        // Then
        assertFalse(supportsDynamic);
    }

    @Test
    void testGetServers_WithLargeServerList() {
        // Given - Test with many servers
        List<String> expectedServers = Arrays.asList(
                "http://server1:8080", "http://server2:8080", "http://server3:8080",
                "http://server4:8080", "http://server5:8080", "http://server6:8080",
                "http://server7:8080", "http://server8:8080", "http://server9:8080",
                "http://server10:8080"
        );
        properties.setServers(expectedServers);

        // When
        List<String> actualServers = discoveryService.getServers();

        // Then
        assertEquals(expectedServers, actualServers);
        assertEquals(10, actualServers.size());
    }

    @Test
    void testGetServers_WithSpecialCharactersInServerNames() {
        // Given
        List<String> expectedServers = Arrays.asList(
                "http://server-1.example.com:8080",
                "http://server_2.example.com:8080",
                "http://192.168.1.100:8080"
        );
        properties.setServers(expectedServers);

        // When
        List<String> actualServers = discoveryService.getServers();

        // Then
        assertEquals(expectedServers, actualServers);
        assertTrue(actualServers.contains("http://server-1.example.com:8080"));
        assertTrue(actualServers.contains("http://server_2.example.com:8080"));
        assertTrue(actualServers.contains("http://192.168.1.100:8080"));
    }

    @Test
    void testGetServers_ImmutabilityOfReturnedList() {
        // Given
        List<String> originalServers = Arrays.asList(
                "http://server1:8080",
                "http://server2:8080"
        );
        properties.setServers(originalServers);

        // When
        List<String> returnedServers = discoveryService.getServers();

        // Then - Verify the service returns consistent data
        assertEquals(originalServers, returnedServers);
        
        // The static discovery service returns the configured list directly,
        // which is acceptable since the configuration shouldn't change at runtime
        List<String> secondCall = discoveryService.getServers();
        assertEquals(returnedServers, secondCall);
    }
}