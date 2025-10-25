package com.codapayments.roundRobin.service;

import java.util.List;

/**
 * Interface for discovering backend servers dynamically
 */
public interface ServerDiscoveryService {
    
    /**
     * Get the list of available backend servers
     * @return List of server URLs
     */
    List<String> getServers();
    
    /**
     * Get the name of the discovery strategy
     * @return Strategy name
     */
    String getStrategyName();
    
    /**
     * Check if the discovery service supports dynamic updates
     * @return true if servers can be updated without restart
     */
    boolean supportsDynamicUpdates();
}