package com.codapayments.roundRobin.controller;

import com.codapayments.roundRobin.model.ServerHealth;
import com.codapayments.roundRobin.service.LoadBalancerService;
import com.codapayments.roundRobin.service.ServerDiscoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private LoadBalancerService loadBalancerService;

    @Mock
    private ServerDiscoveryService serverDiscoveryService;

    private AdminController adminController;

    @BeforeEach
    void setUp() {
        adminController = new AdminController(loadBalancerService, serverDiscoveryService);
    }

    @Test
    void shouldReturnServerHealthStatuses() {
        // Given
        List<ServerHealth> serverHealthList = List.of(
                new ServerHealth("http://localhost:8081", 100L),
                new ServerHealth("http://localhost:8082", 150L)
        );
        when(loadBalancerService.getServerStatuses()).thenReturn(serverHealthList);

        // When
        ResponseEntity<List<ServerHealth>> response = adminController.getServerHealth();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(serverHealthList, response.getBody());
        verify(loadBalancerService).getServerStatuses();
    }

    @Test
    void shouldReturnLoadBalancingStrategy() {
        // Given
        String strategyName = "Round Robin";
        when(loadBalancerService.getLoadBalancingStrategy()).thenReturn(strategyName);

        // When
        ResponseEntity<Map<String, String>> response = adminController.getLoadBalancingStrategy();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(strategyName, response.getBody().get("strategy"));
        verify(loadBalancerService).getLoadBalancingStrategy();
    }

    @Test
    void shouldReturnStatistics() {
        // Given
        ServerHealth healthyServer1 = new ServerHealth("http://localhost:8081", 100L);
        healthyServer1.setHealthy(true);
        
        ServerHealth healthyServer2 = new ServerHealth("http://localhost:8082", 200L);
        healthyServer2.setHealthy(true);
        
        ServerHealth unhealthyServer = new ServerHealth("http://localhost:8083", 150L);
        unhealthyServer.setHealthy(false);
        
        List<ServerHealth> serverHealthList = List.of(healthyServer1, healthyServer2, unhealthyServer);
        String strategyName = "Round Robin";
        
        when(loadBalancerService.getServerStatuses()).thenReturn(serverHealthList);
        when(loadBalancerService.getLoadBalancingStrategy()).thenReturn(strategyName);

        // When
        ResponseEntity<Map<String, Object>> response = adminController.getStatistics();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> stats = response.getBody();
        assertEquals(3L, stats.get("totalServers"));
        assertEquals(2L, stats.get("healthyServers"));
        assertEquals(1L, stats.get("unhealthyServers"));
        assertEquals(150L, stats.get("averageResponseTime")); // (100 + 200) / 2 = 150
        assertEquals(strategyName, stats.get("strategy"));
    }

    @Test
    void shouldReturnStatisticsWithNoHealthyServers() {
        // Given
        ServerHealth unhealthyServer1 = new ServerHealth("http://localhost:8081", 100L);
        unhealthyServer1.setHealthy(false);
        
        ServerHealth unhealthyServer2 = new ServerHealth("http://localhost:8082", 200L);
        unhealthyServer2.setHealthy(false);
        
        List<ServerHealth> serverHealthList = List.of(unhealthyServer1, unhealthyServer2);
        String strategyName = "Round Robin";
        
        when(loadBalancerService.getServerStatuses()).thenReturn(serverHealthList);
        when(loadBalancerService.getLoadBalancingStrategy()).thenReturn(strategyName);

        // When
        ResponseEntity<Map<String, Object>> response = adminController.getStatistics();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> stats = response.getBody();
        assertEquals(2L, stats.get("totalServers"));
        assertEquals(0L, stats.get("healthyServers"));
        assertEquals(2L, stats.get("unhealthyServers"));
        assertEquals(0L, stats.get("averageResponseTime")); // No healthy servers
        assertEquals(strategyName, stats.get("strategy"));
    }

    @Test
    void shouldReturnStatisticsWithEmptyServerList() {
        // Given
        List<ServerHealth> emptyServerList = List.of();
        String strategyName = "Round Robin";
        
        when(loadBalancerService.getServerStatuses()).thenReturn(emptyServerList);
        when(loadBalancerService.getLoadBalancingStrategy()).thenReturn(strategyName);

        // When
        ResponseEntity<Map<String, Object>> response = adminController.getStatistics();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> stats = response.getBody();
        assertEquals(0L, stats.get("totalServers"));
        assertEquals(0L, stats.get("healthyServers"));
        assertEquals(0L, stats.get("unhealthyServers"));
        assertEquals(0L, stats.get("averageResponseTime"));
        assertEquals(strategyName, stats.get("strategy"));
    }

    @Test
    void shouldCalculateCorrectAverageResponseTime() {
        // Given
        ServerHealth server1 = new ServerHealth("http://localhost:8081", 50L);
        server1.setHealthy(true);
        
        ServerHealth server2 = new ServerHealth("http://localhost:8082", 100L);
        server2.setHealthy(true);
        
        ServerHealth server3 = new ServerHealth("http://localhost:8083", 200L);
        server3.setHealthy(true);
        
        List<ServerHealth> serverHealthList = List.of(server1, server2, server3);
        
        when(loadBalancerService.getServerStatuses()).thenReturn(serverHealthList);
        when(loadBalancerService.getLoadBalancingStrategy()).thenReturn("Test Strategy");

        // When
        ResponseEntity<Map<String, Object>> response = adminController.getStatistics();

        // Then
        Map<String, Object> stats = response.getBody();
        assertEquals(117L, stats.get("averageResponseTime")); // (50 + 100 + 200) / 3 = 116.67 -> 117 (rounded)
    }
}