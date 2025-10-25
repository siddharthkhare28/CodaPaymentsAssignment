package com.codapayments.roundRobin.service;

import com.codapayments.roundRobin.config.RoundRobinProperties;
import com.codapayments.roundRobin.model.ServerHealth;
import com.codapayments.roundRobin.service.ServerDiscoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HealthCheckServiceTest {

    @Mock
    private WebClient.Builder webClientBuilder;
    
    @Mock
    private WebClient webClient;
    
    @Mock
    private ServerDiscoveryService serverDiscoveryService;
    
    private RoundRobinProperties properties;
    private HealthCheckService healthCheckService;

    @BeforeEach
    void setUp() {
        properties = new RoundRobinProperties();
        properties.setServers(List.of("http://localhost:8081", "http://localhost:8082"));
        properties.setHealthCheckInterval(1000L);
        properties.setHealthCheckTimeoutSeconds(3);
        properties.setSlowThresholdMs(500L);
        properties.setInitialLatencyMs(100L);

        when(webClientBuilder.build()).thenReturn(webClient);
        when(serverDiscoveryService.getServers()).thenReturn(List.of("http://localhost:8081", "http://localhost:8082"));
        when(serverDiscoveryService.supportsDynamicUpdates()).thenReturn(false);
        
        healthCheckService = new HealthCheckService(webClientBuilder, properties, serverDiscoveryService);
    }    @Test
    void shouldInitializeServerHealthOnPostConstruct() {
        // When
        healthCheckService.initializeServerHealth();

        // Then
        List<ServerHealth> allServers = healthCheckService.getAllServers();
        assertEquals(2, allServers.size());
        
        ServerHealth server1 = healthCheckService.getServerHealth("http://localhost:8081");
        ServerHealth server2 = healthCheckService.getServerHealth("http://localhost:8082");
        
        assertNotNull(server1);
        assertNotNull(server2);
        assertTrue(server1.isHealthy());
        assertTrue(server2.isHealthy());
        assertEquals(100L, server1.getAverageResponseTime());
        assertEquals(100L, server2.getAverageResponseTime());
    }

    @Test
    void shouldReturnOnlyHealthyServers() {
        // Given
        healthCheckService.initializeServerHealth();
        healthCheckService.markServerUnhealthy("http://localhost:8082", "Test failure");

        // When
        List<ServerHealth> healthyServers = healthCheckService.getHealthyServers();

        // Then
        assertEquals(1, healthyServers.size());
        assertEquals("http://localhost:8081", healthyServers.get(0).getServerUrl());
    }

    @Test
    void shouldMarkServerUnhealthy() {
        // Given
        healthCheckService.initializeServerHealth();
        ServerHealth server = healthCheckService.getServerHealth("http://localhost:8081");
        assertTrue(server.isHealthy());

        // When
        healthCheckService.markServerUnhealthy("http://localhost:8081", "Connection failed");

        // Then
        assertFalse(server.isHealthy());
    }

    @Test
    void shouldUpdateServerResponseTime() {
        // Given
        healthCheckService.initializeServerHealth();
        ServerHealth server = healthCheckService.getServerHealth("http://localhost:8081");
        long initialResponseTime = server.getAverageResponseTime();

        // When
        healthCheckService.updateServerResponseTime("http://localhost:8081", 200L);

        // Then
        long newResponseTime = server.getAverageResponseTime();
        assertTrue(newResponseTime > initialResponseTime);
        assertTrue(newResponseTime < 200L); // Should be weighted average
    }

    @Test
    void shouldMarkSlowServerAsUnhealthy() {
        // Given
        healthCheckService.initializeServerHealth();
        ServerHealth server = healthCheckService.getServerHealth("http://localhost:8081");
        assertTrue(server.isHealthy());

        // When - multiple slow responses exceed threshold ratio (need multiple samples for moving average)
        // Default config: 60% of 5 responses need to be slow (> 500ms threshold)
        healthCheckService.updateServerResponseTime("http://localhost:8081", 600L); // Slow
        healthCheckService.updateServerResponseTime("http://localhost:8081", 700L); // Slow
        healthCheckService.updateServerResponseTime("http://localhost:8081", 800L); // Slow
        healthCheckService.updateServerResponseTime("http://localhost:8081", 200L); // Fast
        healthCheckService.updateServerResponseTime("http://localhost:8081", 300L); // Fast
        // 3 out of 5 (60%) are slow, should trigger cooldown

        // Then
        assertFalse(server.isHealthy(), "Server should be marked unhealthy when 60% of responses are slow");
        assertTrue(server.isInSlownessCooldown(), "Server should be in slowness cooldown");
    }

    @Test
    void shouldHandleServerNotFound() {
        // Given
        healthCheckService.initializeServerHealth();

        // When
        healthCheckService.updateServerResponseTime("http://nonexistent:8080", 100L);
        healthCheckService.markServerUnhealthy("http://nonexistent:8080", "Test");

        // Then - should not throw exception
        assertNull(healthCheckService.getServerHealth("http://nonexistent:8080"));
    }

    @Test
    void shouldNotMarkServerAsSlowWithInsufficientData() {
        // Given
        healthCheckService.initializeServerHealth();
        ServerHealth server = healthCheckService.getServerHealth("http://localhost:8081");
        assertTrue(server.isHealthy());

        // When - only one slow response (insufficient for moving average)
        healthCheckService.updateServerResponseTime("http://localhost:8081", 600L); // > 500ms threshold

        // Then - should remain healthy as we need more samples
        assertTrue(server.isHealthy(), "Server should remain healthy with insufficient data for moving average");
        assertFalse(server.isInSlownessCooldown(), "Server should not be in cooldown with insufficient data");
    }

    @Test
    void shouldNotMarkServerAsSlowWithMostlyFastResponses() {
        // Given
        healthCheckService.initializeServerHealth();
        ServerHealth server = healthCheckService.getServerHealth("http://localhost:8081");
        assertTrue(server.isHealthy());

        // When - only 20% of responses are slow (below 60% threshold)
        healthCheckService.updateServerResponseTime("http://localhost:8081", 600L); // Slow
        healthCheckService.updateServerResponseTime("http://localhost:8081", 200L); // Fast
        healthCheckService.updateServerResponseTime("http://localhost:8081", 300L); // Fast
        healthCheckService.updateServerResponseTime("http://localhost:8081", 250L); // Fast
        healthCheckService.updateServerResponseTime("http://localhost:8081", 400L); // Fast
        // 1 out of 5 (20%) are slow, should not trigger cooldown

        // Then
        assertTrue(server.isHealthy(), "Server should remain healthy when most responses are fast");
        assertFalse(server.isInSlownessCooldown(), "Server should not be in cooldown when below threshold");
    }    @Test
    void shouldReturnAllServers() {
        // Given
        healthCheckService.initializeServerHealth();

        // When
        List<ServerHealth> allServers = healthCheckService.getAllServers();

        // Then
        assertEquals(2, allServers.size());
        assertTrue(allServers.stream().anyMatch(s -> s.getServerUrl().equals("http://localhost:8081")));
        assertTrue(allServers.stream().anyMatch(s -> s.getServerUrl().equals("http://localhost:8082")));
    }

    @Test
    void shouldResetConsecutiveFailuresWhenServerBecomesHealthy() {
        // Given
        healthCheckService.initializeServerHealth();
        ServerHealth server = healthCheckService.getServerHealth("http://localhost:8081");
        
        // Mark as unhealthy multiple times
        server.setHealthy(false);
        server.setHealthy(false);
        server.setHealthy(false);
        assertEquals(3, server.getConsecutiveFailures());

        // When - mark as healthy
        server.setHealthy(true);

        // Then
        assertEquals(0, server.getConsecutiveFailures());
    }
}