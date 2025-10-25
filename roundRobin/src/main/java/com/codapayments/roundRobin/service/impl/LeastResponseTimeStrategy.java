package com.codapayments.roundRobin.service.impl;

import com.codapayments.roundRobin.model.ServerHealth;
import com.codapayments.roundRobin.service.LoadBalancingStrategy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "roundrobin.strategy", havingValue = "least-response-time")
public class LeastResponseTimeStrategy implements LoadBalancingStrategy {

    @Override
    public ServerHealth selectServer(List<ServerHealth> healthyServers) {
        if (healthyServers == null || healthyServers.isEmpty()) {
            return null;
        }

        // The healthyServers list is already an immutable snapshot created within synchronized block
        // Find the server with minimum response time, but verify health at selection time
        ServerHealth bestServer = null;
        long bestResponseTime = Long.MAX_VALUE;
        
        for (ServerHealth server : healthyServers) {
            if (server != null && server.isHealthy()) {
                long responseTime = server.getAverageResponseTime();
                if (responseTime < bestResponseTime) {
                    bestResponseTime = responseTime;
                    bestServer = server;
                }
            }
        }
        
        return bestServer;
    }

    @Override
    public String getStrategyName() {
        return "Least Response Time";
    }
}