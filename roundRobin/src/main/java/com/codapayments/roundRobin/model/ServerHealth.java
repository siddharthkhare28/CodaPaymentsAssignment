package com.codapayments.roundRobin.model;

import java.time.Instant;

public class ServerHealth {
    private final String serverUrl;
    private boolean healthy;
    private long averageResponseTime;
    private Instant lastHealthCheck;
    private int consecutiveFailures;
    private Instant lastSlowResponseTime;
    private boolean isInSlownessCooldown;
    private final ResponseTimeWindow responseTimeWindow;

    public ServerHealth(String serverUrl, long initialResponseTime) {
        this(serverUrl, initialResponseTime, 30000, 10); // Default 30 second window, max 10 entries
    }
    
    public ServerHealth(String serverUrl, long initialResponseTime, long windowSizeMs, int maxEntries) {
        this.serverUrl = serverUrl;
        this.healthy = true;
        this.averageResponseTime = initialResponseTime;
        this.lastHealthCheck = Instant.now();
        this.consecutiveFailures = 0;
        this.lastSlowResponseTime = null;
        this.isInSlownessCooldown = false;
        this.responseTimeWindow = new ResponseTimeWindow(windowSizeMs, maxEntries);
    }

    // Getters and setters
    public String getServerUrl() {
        return serverUrl;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
        if (healthy) {
            this.consecutiveFailures = 0;
        } else {
            this.consecutiveFailures++;
        }
    }

    public long getAverageResponseTime() {
        return averageResponseTime;
    }

    public void updateResponseTime(long newResponseTime) {
        // Add to moving window
        responseTimeWindow.addResponseTime(newResponseTime);
        
        // Update traditional weighted moving average (80% old, 20% new)
        this.averageResponseTime = (averageResponseTime * 4 + newResponseTime) / 5;
    }
    
    /**
     * Check if the server should be considered slow based on moving average
     * @param slowThresholdMs Threshold for slow responses
     * @param thresholdRatio Ratio of slow responses required to mark as slow
     * @param minimumEntries Minimum number of entries needed for analysis
     * @return true if server should be marked as slow
     */
    public boolean shouldBeMarkedAsSlow(long slowThresholdMs, double thresholdRatio, int minimumEntries) {
        if (!responseTimeWindow.hasEnoughData(minimumEntries)) {
            return false; // Not enough data yet
        }
        
        double slowRatio = responseTimeWindow.getSlowResponseRatio(slowThresholdMs);
        return slowRatio >= thresholdRatio;
    }
    
    /**
     * Get the current window average response time
     */
    public double getWindowAverageResponseTime() {
        return responseTimeWindow.getAverageResponseTime();
    }
    
    /**
     * Get the number of entries in the response time window
     */
    public int getWindowEntryCount() {
        return responseTimeWindow.getEntryCount();
    }
    
    /**
     * Get the ratio of slow responses in the current window
     */
    public double getSlowResponseRatio(long slowThresholdMs) {
        return responseTimeWindow.getSlowResponseRatio(slowThresholdMs);
    }

    public Instant getLastHealthCheck() {
        return lastHealthCheck;
    }

    public void setLastHealthCheck(Instant lastHealthCheck) {
        this.lastHealthCheck = lastHealthCheck;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public void setConsecutiveFailures(int consecutiveFailures) {
        this.consecutiveFailures = consecutiveFailures;
    }

    public Instant getLastSlowResponseTime() {
        return lastSlowResponseTime;
    }

    public void setLastSlowResponseTime(Instant lastSlowResponseTime) {
        this.lastSlowResponseTime = lastSlowResponseTime;
    }

    public boolean isInSlownessCooldown() {
        return isInSlownessCooldown;
    }

    public void setInSlownessCooldown(boolean inSlownessCooldown) {
        this.isInSlownessCooldown = inSlownessCooldown;
    }

    /**
     * Marks this server as slow and puts it into cooldown period
     */
    public void markAsSlow() {
        this.lastSlowResponseTime = Instant.now();
        this.isInSlownessCooldown = true;
        this.healthy = false;
    }

    /**
     * Checks if the server is still in cooldown period for slowness
     * @param cooldownDurationSeconds Duration of cooldown in seconds
     * @return true if still in cooldown, false if cooldown has expired
     */
    public boolean isStillInSlownessCooldown(long cooldownDurationSeconds) {
        if (!isInSlownessCooldown || lastSlowResponseTime == null) {
            return false;
        }
        
        Instant cooldownExpiry = lastSlowResponseTime.plusSeconds(cooldownDurationSeconds);
        return Instant.now().isBefore(cooldownExpiry);
    }

    /**
     * Clears the slowness cooldown, allowing the server to become healthy again
     */
    public void clearSlownessCooldown() {
        this.isInSlownessCooldown = false;
        this.lastSlowResponseTime = null;
    }

    @Override
    public String toString() {
        return String.format("ServerHealth{url='%s', healthy=%s, avgResponseTime=%dms, failures=%d, cooldown=%s}", 
                serverUrl, healthy, averageResponseTime, consecutiveFailures, isInSlownessCooldown);
    }
}