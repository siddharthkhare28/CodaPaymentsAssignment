package com.codapayments.roundRobin.service;

import com.codapayments.roundRobin.config.RoundRobinProperties;
import com.codapayments.roundRobin.model.ServerHealth;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class HealthCheckService {

    private static final Logger logger = LoggerFactory.getLogger(HealthCheckService.class);
    
    private final WebClient webClient;
    private final RoundRobinProperties properties;
    private final ServerDiscoveryService serverDiscoveryService;
    private final Map<String, ServerHealth> serverHealthMap = new ConcurrentHashMap<>();
    private final ReadWriteLock serverListLock = new ReentrantReadWriteLock();

    public HealthCheckService(WebClient.Builder webClientBuilder, 
                            RoundRobinProperties properties,
                            ServerDiscoveryService serverDiscoveryService) {
        this.webClient = webClientBuilder.build();
        this.properties = properties;
        this.serverDiscoveryService = serverDiscoveryService;
    }

    @PostConstruct
    public void initializeServerHealth() {
        refreshServerList();
    }
    
    private void refreshServerList() {
        List<String> currentServers = serverDiscoveryService.getServers();
        
        if (currentServers != null) {
            serverListLock.writeLock().lock();
            try {
                // Add new servers
                currentServers.forEach(server -> {
                    if (!serverHealthMap.containsKey(server)) {
                        ServerHealth health = new ServerHealth(server, properties.getInitialLatencyMs(), 
                                properties.getSlownessWindowTimeMs(), properties.getSlownessWindowSize());
                        serverHealthMap.put(server, health);
                        logger.info("Initialized health tracking for server: {}", server);
                    }
                });
                
                // Remove servers that are no longer in the list
                serverHealthMap.keySet().removeIf(server -> {
                    if (!currentServers.contains(server)) {
                        logger.info("Removed server from health tracking: {}", server);
                        return true;
                    }
                    return false;
                });
            } finally {
                serverListLock.writeLock().unlock();
            }
        }
    }

    @Scheduled(fixedDelayString = "#{@roundRobinProperties.healthCheckInterval}")
    public void performHealthChecks() {
        // Refresh server list for dynamic discovery
        if (serverDiscoveryService.supportsDynamicUpdates()) {
            refreshServerList();
        }
        
        logger.debug("Starting health checks for {} servers", serverHealthMap.size());
        
        serverHealthMap.values().forEach(this::checkServerHealth);
    }

    private void checkServerHealth(ServerHealth serverHealth) {
        String server = serverHealth.getServerUrl();
        Instant start = Instant.now();

        webClient.get()
                .uri(server + "/actuator/health")
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(properties.getHealthCheckTimeoutSeconds()))
                .map(response -> "UP".equalsIgnoreCase(String.valueOf(response.get("status"))))
                .onErrorReturn(false)
                .doOnNext(isHealthy -> {
                    long responseTime = Duration.between(start, Instant.now()).toMillis();
                    updateServerHealth(serverHealth, isHealthy, responseTime);
                })
                .subscribe();
    }

    private void updateServerHealth(ServerHealth serverHealth, boolean isHealthy, long responseTime) {
        boolean wasHealthy = serverHealth.isHealthy();
        
        // Check if server is in slowness cooldown
        if (serverHealth.isStillInSlownessCooldown(properties.getSlownessCooldownSeconds())) {
            // Server is still in cooldown period for being slow
            logger.debug("Server {} still in slowness cooldown, keeping unhealthy", 
                    serverHealth.getServerUrl());
            serverHealth.setHealthy(false);
            serverHealth.setLastHealthCheck(Instant.now());
            return;
        }
        
        // Clear cooldown if it has expired
        if (serverHealth.isInSlownessCooldown() && 
            !serverHealth.isStillInSlownessCooldown(properties.getSlownessCooldownSeconds())) {
            serverHealth.clearSlownessCooldown();
            logger.info("Server {} slowness cooldown expired, can become healthy again", 
                    serverHealth.getServerUrl());
        }
        
        // Normal health update
        serverHealth.setHealthy(isHealthy);
        serverHealth.setLastHealthCheck(Instant.now());
        
        // Note: We don't update response time or check for slowness here
        // Health check response times are administrative and not indicative of user request performance
        // Slowness detection should only be based on actual user request response times

        boolean isNowHealthy = serverHealth.isHealthy();
        if (wasHealthy != isNowHealthy) {
            String reason = "";
            if (!isNowHealthy && serverHealth.isInSlownessCooldown()) {
                double slowRatio = serverHealth.getSlowResponseRatio(properties.getSlowThresholdMs());
                double avgResponseTime = serverHealth.getWindowAverageResponseTime();
                reason = String.format(" (moving avg slowness: %.1f%% slow responses, avg: %.1fms, cooldown: %ds)", 
                        slowRatio * 100, avgResponseTime, properties.getSlownessCooldownSeconds());
            }
            logger.info("Server {} health changed: {} -> {} (response time: {}ms){}", 
                    serverHealth.getServerUrl(), wasHealthy ? "UP" : "DOWN", 
                    isNowHealthy ? "UP" : "DOWN", responseTime, reason);
        } else {
            logger.debug("Server {} health check: {} (response time: {}ms)", 
                    serverHealth.getServerUrl(), isNowHealthy ? "UP" : "DOWN", responseTime);
        }
    }

    public List<ServerHealth> getHealthyServers() {
        serverListLock.readLock().lock();
        try {
            // Create a list of servers that are currently healthy with atomic snapshot of their state
            // This ensures consistent state during load balancing selection
            return serverHealthMap.values().stream()
                    .filter(server -> {
                        // Check health status atomically within the read lock
                        // This prevents state changes during filtering
                        return server.isHealthy() && !server.isStillInSlownessCooldown(properties.getSlownessCooldownSeconds());
                    })
                    .toList(); // Creates immutable list with consistent server states
        } finally {
            serverListLock.readLock().unlock();
        }
    }

    public List<ServerHealth> getAllServers() {
        serverListLock.readLock().lock();
        try {
            return List.copyOf(serverHealthMap.values());
        } finally {
            serverListLock.readLock().unlock();
        }
    }

    public ServerHealth getServerHealth(String serverUrl) {
        return serverHealthMap.get(serverUrl);
    }

    public void markServerUnhealthy(String serverUrl, String reason) {
        ServerHealth health = serverHealthMap.get(serverUrl);
        if (health != null) {
            health.setHealthy(false);
            logger.warn("Marked server {} as unhealthy: {}", serverUrl, reason);
        }
    }

    public void updateServerResponseTime(String serverUrl, long responseTime) {
        ServerHealth health = serverHealthMap.get(serverUrl);
        if (health != null) {
            health.updateResponseTime(responseTime);
            
            // Check if response pattern indicates slowness (moving average approach)
            if (health.shouldBeMarkedAsSlow(
                    properties.getSlowThresholdMs(), 
                    properties.getSlownessThresholdRatio(), 
                    properties.getSlownessWindowSize()) && !health.isInSlownessCooldown()) {
                
                health.markAsSlow();
                double slowRatio = health.getSlowResponseRatio(properties.getSlowThresholdMs());
                double avgResponseTime = health.getWindowAverageResponseTime();
                int sampleCount = health.getWindowEntryCount();
                
                logger.warn("Server {} marked as slow based on moving average: {}% of {} responses " +
                        "exceeded {}ms threshold (avg: {}ms) - applying {}s cooldown",
                        serverUrl, slowRatio * 100, sampleCount, 
                        properties.getSlowThresholdMs(), avgResponseTime, 
                        properties.getSlownessCooldownSeconds());
            }
        }
    }
}