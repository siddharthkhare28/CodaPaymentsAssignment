package com.codapayments.roundRobin.service.impl;

import com.codapayments.roundRobin.model.ServerHealth;
import com.codapayments.roundRobin.service.LoadBalancingStrategy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RoundRobinStrategy implements LoadBalancingStrategy {

    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public ServerHealth selectServer(List<ServerHealth> healthyServers) {
        if (healthyServers == null || healthyServers.isEmpty()) {
            return null;
        }

        // The healthyServers list is already an immutable snapshot created within synchronized block
        // However, we still validate the selected server's health at selection time for extra safety
        int serverCount = healthyServers.size();
        int attempts = 0;
        int maxAttempts = serverCount; // Try each server once
        
        while (attempts < maxAttempts) {
            int index = Math.abs(counter.getAndIncrement() % serverCount);
            ServerHealth selectedServer = healthyServers.get(index);
            
            // Double-check the server is still healthy at selection time
            // This provides additional protection against state changes
            if (selectedServer != null && selectedServer.isHealthy()) {
                return selectedServer;
            }
            
            attempts++;
        }
        
        // If no healthy server found after trying all, return null
        return null;
    }

    @Override
    public String getStrategyName() {
        return "Round Robin";
    }
}