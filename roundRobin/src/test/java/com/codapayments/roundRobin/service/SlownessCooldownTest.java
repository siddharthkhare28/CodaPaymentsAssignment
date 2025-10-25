package com.codapayments.roundRobin.service;

import com.codapayments.roundRobin.config.RoundRobinProperties;
import com.codapayments.roundRobin.model.ServerHealth;
import com.codapayments.roundRobin.service.impl.StaticServerDiscoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SlownessCooldownTest {

    private HealthCheckService healthCheckService;
    private RoundRobinProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RoundRobinProperties();
        properties.setServers(List.of("http://localhost:8081"));
        properties.setSlowThresholdMs(500);
        properties.setSlownessCooldownSeconds(2); // Short cooldown for testing
        properties.setHealthCheckTimeoutSeconds(3);

        ServerDiscoveryService discoveryService = new StaticServerDiscoveryService(properties);
        healthCheckService = new HealthCheckService(WebClient.builder(), properties, discoveryService);
    }

    @Test
    void testSlowServerCooldownBehavior() throws InterruptedException {
        String serverUrl = "http://localhost:8081";
        
        // Initialize server health for all configured servers
        healthCheckService.initializeServerHealth();
        ServerHealth serverHealth = healthCheckService.getAllServers().get(0);
        
        // Verify initial state
        assertTrue(serverHealth.isHealthy());
        assertFalse(serverHealth.isInSlownessCooldown());
        
        // Simulate multiple slow responses to trigger moving average threshold
        // (Need 60% of responses to be slow to trigger cooldown)
        healthCheckService.updateServerResponseTime(serverUrl, 600); // Slow
        healthCheckService.updateServerResponseTime(serverUrl, 700); // Slow
        healthCheckService.updateServerResponseTime(serverUrl, 800); // Slow
        healthCheckService.updateServerResponseTime(serverUrl, 200); // Fast
        healthCheckService.updateServerResponseTime(serverUrl, 900); // Slow
        // 4 out of 5 (80%) are slow, should trigger cooldown
        
        // Server should be marked unhealthy and in cooldown
        assertFalse(serverHealth.isHealthy());
        assertTrue(serverHealth.isInSlownessCooldown());
        assertNotNull(serverHealth.getLastSlowResponseTime());
        
        // Try to mark as healthy again immediately - should remain unhealthy due to cooldown
        healthCheckService.updateServerResponseTime(serverUrl, 100); // Fast response
        assertFalse(serverHealth.isHealthy()); // Still unhealthy due to cooldown
        assertTrue(serverHealth.isInSlownessCooldown());
        
        // Wait for cooldown to expire (2 seconds + small buffer)
        Thread.sleep(2100);
        
        // Now fast response should make it healthy again
        healthCheckService.updateServerResponseTime(serverUrl, 100);
        // The server should still be marked unhealthy until next health check evaluates cooldown
        
        // Verify cooldown state can be checked
        assertFalse(serverHealth.isStillInSlownessCooldown(properties.getSlownessCooldownSeconds()));
    }

    @Test
    void testSlowServerHealthModel() {
        ServerHealth serverHealth = new ServerHealth("http://test:8080", 100);
        
        // Initial state
        assertTrue(serverHealth.isHealthy());
        assertFalse(serverHealth.isInSlownessCooldown());
        assertNull(serverHealth.getLastSlowResponseTime());
        
        // Mark as slow
        Instant beforeSlow = Instant.now();
        serverHealth.markAsSlow();
        Instant afterSlow = Instant.now();
        
        // Verify slow state
        assertFalse(serverHealth.isHealthy());
        assertTrue(serverHealth.isInSlownessCooldown());
        assertNotNull(serverHealth.getLastSlowResponseTime());
        assertTrue(serverHealth.getLastSlowResponseTime().isAfter(beforeSlow.minusSeconds(1)));
        assertTrue(serverHealth.getLastSlowResponseTime().isBefore(afterSlow.plusSeconds(1)));
        
        // Verify cooldown check
        assertTrue(serverHealth.isStillInSlownessCooldown(60)); // 60 second cooldown
        assertFalse(serverHealth.isStillInSlownessCooldown(0)); // No cooldown
        
        // Clear cooldown
        serverHealth.clearSlownessCooldown();
        assertFalse(serverHealth.isInSlownessCooldown());
        assertNull(serverHealth.getLastSlowResponseTime());
    }

    @Test
    void testCooldownExpiration() throws InterruptedException {
        ServerHealth serverHealth = new ServerHealth("http://test:8080", 100);
        
        // Mark as slow
        serverHealth.markAsSlow();
        assertTrue(serverHealth.isStillInSlownessCooldown(1)); // 1 second cooldown
        
        // Wait for cooldown to expire
        Thread.sleep(1100);
        assertFalse(serverHealth.isStillInSlownessCooldown(1)); // Cooldown expired
    }

    @Test
    void testToStringIncludesCooldownStatus() {
        ServerHealth serverHealth = new ServerHealth("http://test:8080", 100);
        
        // Normal state
        String normalString = serverHealth.toString();
        assertTrue(normalString.contains("cooldown=false"));
        
        // In cooldown state
        serverHealth.markAsSlow();
        String cooldownString = serverHealth.toString();
        assertTrue(cooldownString.contains("cooldown=true"));
    }
}