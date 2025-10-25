package com.codapayments.roundRobin.model;

import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe sliding window to track response times over a specified time period
 */
public class ResponseTimeWindow {
    
    private final ConcurrentLinkedQueue<ResponseTimeEntry> responseEntries;
    private final long windowSizeMs;
    private final int maxEntries;
    private final AtomicLong totalResponseTime;
    private final AtomicLong entryCount;
    
    public ResponseTimeWindow(long windowSizeMs, int maxEntries) {
        this.responseEntries = new ConcurrentLinkedQueue<>();
        this.windowSizeMs = windowSizeMs;
        this.maxEntries = maxEntries;
        this.totalResponseTime = new AtomicLong(0);
        this.entryCount = new AtomicLong(0);
    }
    
    /**
     * Add a new response time to the window
     */
    public void addResponseTime(long responseTimeMs) {
        Instant now = Instant.now();
        ResponseTimeEntry entry = new ResponseTimeEntry(responseTimeMs, now);
        
        // Add new entry
        responseEntries.offer(entry);
        totalResponseTime.addAndGet(responseTimeMs);
        entryCount.incrementAndGet();
        
        // Clean up old entries
        cleanupOldEntries(now);
        
        // Limit window size by number of entries
        while (responseEntries.size() > maxEntries) {
            ResponseTimeEntry removed = responseEntries.poll();
            if (removed != null) {
                totalResponseTime.addAndGet(-removed.responseTimeMs);
                entryCount.decrementAndGet();
            }
        }
    }
    
    /**
     * Get the average response time in the current window
     */
    public double getAverageResponseTime() {
        cleanupOldEntries(Instant.now());
        long count = entryCount.get();
        return count > 0 ? (double) totalResponseTime.get() / count : 0.0;
    }
    
    /**
     * Get the ratio of slow responses in the current window
     */
    public double getSlowResponseRatio(long slowThresholdMs) {
        cleanupOldEntries(Instant.now());
        
        if (responseEntries.isEmpty()) {
            return 0.0;
        }
        
        long slowCount = responseEntries.stream()
                .mapToLong(entry -> entry.responseTimeMs > slowThresholdMs ? 1 : 0)
                .sum();
        
        return (double) slowCount / responseEntries.size();
    }
    
    /**
     * Get the number of entries in the current window
     */
    public int getEntryCount() {
        cleanupOldEntries(Instant.now());
        return responseEntries.size();
    }
    
    /**
     * Check if the window has enough data for reliable analysis
     */
    public boolean hasEnoughData(int minimumEntries) {
        cleanupOldEntries(Instant.now());
        return responseEntries.size() >= minimumEntries;
    }
    
    /**
     * Clear all entries from the window
     */
    public void clear() {
        responseEntries.clear();
        totalResponseTime.set(0);
        entryCount.set(0);
    }
    
    /**
     * Remove entries older than the window time
     */
    private void cleanupOldEntries(Instant now) {
        Instant cutoffTime = now.minusMillis(windowSizeMs);
        
        while (!responseEntries.isEmpty()) {
            ResponseTimeEntry oldest = responseEntries.peek();
            if (oldest != null && oldest.timestamp.isBefore(cutoffTime)) {
                ResponseTimeEntry removed = responseEntries.poll();
                if (removed != null) {
                    totalResponseTime.addAndGet(-removed.responseTimeMs);
                    entryCount.decrementAndGet();
                }
            } else {
                break;
            }
        }
    }
    
    /**
     * Internal class to represent a response time entry with timestamp
     */
    private static class ResponseTimeEntry {
        final long responseTimeMs;
        final Instant timestamp;
        
        ResponseTimeEntry(long responseTimeMs, Instant timestamp) {
            this.responseTimeMs = responseTimeMs;
            this.timestamp = timestamp;
        }
    }
}