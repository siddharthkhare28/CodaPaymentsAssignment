package com.codapayments.roundRobin.service.impl;

import com.codapayments.roundRobin.config.RoundRobinProperties;
import com.codapayments.roundRobin.service.ServerDiscoveryService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Static server discovery from application.yaml configuration
 */
@Service("staticServerDiscovery")
public class StaticServerDiscoveryService implements ServerDiscoveryService {
    
    private final RoundRobinProperties properties;
    
    public StaticServerDiscoveryService(RoundRobinProperties properties) {
        this.properties = properties;
    }
    
    @Override
    public List<String> getServers() {
        return properties.getServers();
    }
    
    @Override
    public String getStrategyName() {
        return "Static Configuration";
    }
    
    @Override
    public boolean supportsDynamicUpdates() {
        return false;
    }
}