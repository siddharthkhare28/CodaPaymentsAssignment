package com.codapayments.roundRobin.controller;

import com.codapayments.roundRobin.model.ServerHealth;
import com.codapayments.roundRobin.service.LoadBalancerService;
import com.codapayments.roundRobin.service.ServerDiscoveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final LoadBalancerService loadBalancerService;
    private final ServerDiscoveryService serverDiscoveryService;

    public AdminController(LoadBalancerService loadBalancerService, ServerDiscoveryService serverDiscoveryService) {
        this.loadBalancerService = loadBalancerService;
        this.serverDiscoveryService = serverDiscoveryService;
    }

    /**
     * Health check endpoint to view server statuses
     */
    @GetMapping("/health")
    public ResponseEntity<List<ServerHealth>> getServerHealth() {
        List<ServerHealth> serverStatuses = loadBalancerService.getServerStatuses();
        return ResponseEntity.ok(serverStatuses);
    }

    /**
     * Get current load balancing strategy
     */
    @GetMapping("/strategy")
    public ResponseEntity<Map<String, String>> getLoadBalancingStrategy() {
        String strategy = loadBalancerService.getLoadBalancingStrategy();
        return ResponseEntity.ok(Map.of("strategy", strategy));
    }

    /**
     * Get load balancer statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        List<ServerHealth> servers = loadBalancerService.getServerStatuses();
        
        long healthyCount = servers.stream().filter(ServerHealth::isHealthy).count();
        long totalCount = servers.size();
        double avgResponseTime = servers.stream()
                .filter(ServerHealth::isHealthy)
                .mapToLong(ServerHealth::getAverageResponseTime)
                .average()
                .orElse(0.0);

        Map<String, Object> stats = Map.of(
                "totalServers", totalCount,
                "healthyServers", healthyCount,
                "unhealthyServers", totalCount - healthyCount,
                "averageResponseTime", Math.round(avgResponseTime),
                "strategy", loadBalancerService.getLoadBalancingStrategy()
        );

        return ResponseEntity.ok(stats);
    }

    /**
     * Server discovery information endpoint
     */
    @GetMapping("/discovery")
    public ResponseEntity<Map<String, Object>> getServerDiscoveryInfo() {
        List<String> currentServers = serverDiscoveryService.getServers();
        
        Map<String, Object> discoveryInfo = Map.of(
                "strategyName", serverDiscoveryService.getStrategyName(),
                "supportsDynamicUpdates", serverDiscoveryService.supportsDynamicUpdates(),
                "discoveredServers", currentServers != null ? currentServers : List.of(),
                "serverCount", currentServers != null ? currentServers.size() : 0
        );

        return ResponseEntity.ok(discoveryInfo);
    }
}