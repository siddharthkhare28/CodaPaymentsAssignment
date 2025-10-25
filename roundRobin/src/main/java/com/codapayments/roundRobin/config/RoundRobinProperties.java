package com.codapayments.roundRobin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "roundrobin")
public class RoundRobinProperties {

    private List<String> servers;
    private long healthCheckInterval = 10000; // 10 seconds
    private long slowThresholdMs = 1000; // 1 second
    private long slownessCooldownSeconds = 60; // 1 minute cooldown for slow servers
    private long initialLatencyMs = 200; // 200ms
    private int requestTimeoutSeconds = 5;
    private int healthCheckTimeoutSeconds = 3;
    
    // Moving average configuration for slowness detection
    private int slownessWindowSize = 5; // Number of recent responses to consider
    private long slownessWindowTimeMs = 30000; // 30 seconds time window
    private double slownessThresholdRatio = 0.6; // 60% of responses in window must be slow
    
    // Server discovery configuration
    private String serverDiscoveryStrategy = "static"; // static or file
    private String serverDiscoveryFilePath = "servers.txt";

    public List<String> getServers() {
        return servers;
    }

    public void setServers(List<String> servers) {
        this.servers = servers;
    }

    public long getHealthCheckInterval() {
        return healthCheckInterval;
    }

    public void setHealthCheckInterval(long healthCheckInterval) {
        this.healthCheckInterval = healthCheckInterval;
    }

    public long getSlowThresholdMs() {
        return slowThresholdMs;
    }

    public void setSlowThresholdMs(long slowThresholdMs) {
        this.slowThresholdMs = slowThresholdMs;
    }

    public long getSlownessCooldownSeconds() {
        return slownessCooldownSeconds;
    }

    public void setSlownessCooldownSeconds(long slownessCooldownSeconds) {
        this.slownessCooldownSeconds = slownessCooldownSeconds;
    }

    public long getInitialLatencyMs() {
        return initialLatencyMs;
    }

    public void setInitialLatencyMs(long initialLatencyMs) {
        this.initialLatencyMs = initialLatencyMs;
    }

    public int getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    public int getHealthCheckTimeoutSeconds() {
        return healthCheckTimeoutSeconds;
    }

    public void setHealthCheckTimeoutSeconds(int healthCheckTimeoutSeconds) {
        this.healthCheckTimeoutSeconds = healthCheckTimeoutSeconds;
    }

    public String getServerDiscoveryStrategy() {
        return serverDiscoveryStrategy;
    }

    public void setServerDiscoveryStrategy(String serverDiscoveryStrategy) {
        this.serverDiscoveryStrategy = serverDiscoveryStrategy;
    }

    public String getServerDiscoveryFilePath() {
        return serverDiscoveryFilePath;
    }

    public void setServerDiscoveryFilePath(String serverDiscoveryFilePath) {
        this.serverDiscoveryFilePath = serverDiscoveryFilePath;
    }

    public int getSlownessWindowSize() {
        return slownessWindowSize;
    }

    public void setSlownessWindowSize(int slownessWindowSize) {
        this.slownessWindowSize = slownessWindowSize;
    }

    public long getSlownessWindowTimeMs() {
        return slownessWindowTimeMs;
    }

    public void setSlownessWindowTimeMs(long slownessWindowTimeMs) {
        this.slownessWindowTimeMs = slownessWindowTimeMs;
    }

    public double getSlownessThresholdRatio() {
        return slownessThresholdRatio;
    }

    public void setSlownessThresholdRatio(double slownessThresholdRatio) {
        this.slownessThresholdRatio = slownessThresholdRatio;
    }
}