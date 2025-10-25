package com.codapayments.roundRobin.service;

import com.codapayments.roundRobin.model.ServerHealth;

import java.util.List;

public interface LoadBalancingStrategy {
    
    /**
     * Select the next server to route the request to
     * @param healthyServers List of healthy servers
     * @return The selected server, or null if no servers available
     */
    ServerHealth selectServer(List<ServerHealth> healthyServers);
    
    /**
     * Get the name/type of this load balancing strategy
     * @return Strategy name
     */
    String getStrategyName();
}