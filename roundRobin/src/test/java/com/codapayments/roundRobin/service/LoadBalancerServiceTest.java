package com.codapayments.roundRobin.service;

import com.codapayments.roundRobin.config.RoundRobinProperties;
import com.codapayments.roundRobin.model.ProxyRequest;
import com.codapayments.roundRobin.model.ServerHealth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoadBalancerServiceTest {

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @Mock
    private HealthCheckService healthCheckService;

    @Mock
    private LoadBalancingStrategy loadBalancingStrategy;

    private RoundRobinProperties properties;
    private LoadBalancerService loadBalancerService;

    @BeforeEach
    void setUp() {
        properties = new RoundRobinProperties();
        properties.setRequestTimeoutSeconds(5);
        properties.setSlowThresholdMs(500L);

        when(webClientBuilder.build()).thenReturn(webClient);
        
        loadBalancerService = new LoadBalancerService(webClientBuilder, healthCheckService, 
                loadBalancingStrategy, properties);
    }

    @Test
    void shouldReturnServiceUnavailableWhenNoHealthyServers() {
        // Given
        when(healthCheckService.getHealthyServers()).thenReturn(List.of());

        ProxyRequest<Object> request = new ProxyRequest<>("/api/test", HttpMethod.GET, 
                Map.of(), Map.of(), null);

        // When
        Mono<ResponseEntity<Object>> result = loadBalancerService.forwardRequest(request);

        // Then
        ResponseEntity<Object> response = result.block();
        assertNotNull(response);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }

    @Test
    void shouldReturnServerStatuses() {
        // Given
        List<ServerHealth> mockServers = List.of(
                new ServerHealth("http://localhost:8081", 100L),
                new ServerHealth("http://localhost:8082", 150L)
        );
        when(healthCheckService.getAllServers()).thenReturn(mockServers);

        // When
        List<ServerHealth> result = loadBalancerService.getServerStatuses();

        // Then
        assertEquals(2, result.size());
        assertEquals(mockServers, result);
    }

    @Test
    void shouldReturnLoadBalancingStrategy() {
        // Given
        when(loadBalancingStrategy.getStrategyName()).thenReturn("Test Strategy");

        // When
        String strategy = loadBalancerService.getLoadBalancingStrategy();

        // Then
        assertEquals("Test Strategy", strategy);
    }

    @Test
    void shouldCallHealthCheckServiceForHealthyServers() {
        // Given
        when(healthCheckService.getHealthyServers()).thenReturn(List.of());

        ProxyRequest<Object> request = new ProxyRequest<>("/api/test", HttpMethod.GET, 
                Map.of(), Map.of(), null);

        // When
        loadBalancerService.forwardRequest(request).block();

        // Then
        verify(healthCheckService).getHealthyServers();
    }

    @Test
    void shouldCallLoadBalancingStrategyForServerSelection() {
        // Given
        List<ServerHealth> healthyServers = List.of(new ServerHealth("http://localhost:8081", 100L));
        when(healthCheckService.getHealthyServers()).thenReturn(healthyServers);
        when(loadBalancingStrategy.selectServer(healthyServers)).thenReturn(null);

        ProxyRequest<Object> request = new ProxyRequest<>("/api/test", HttpMethod.GET, 
                Map.of(), Map.of(), null);

        // When
        loadBalancerService.forwardRequest(request).block();

        // Then
        verify(loadBalancingStrategy).selectServer(healthyServers);
    }

    @Test
    void shouldReturnServiceUnavailableWhenStrategyReturnsNull() {
        // Given
        List<ServerHealth> healthyServers = List.of(new ServerHealth("http://localhost:8081", 100L));
        when(healthCheckService.getHealthyServers()).thenReturn(healthyServers);
        when(loadBalancingStrategy.selectServer(healthyServers)).thenReturn(null);

        ProxyRequest<Object> request = new ProxyRequest<>("/api/test", HttpMethod.GET, 
                Map.of(), Map.of(), null);

        // When
        Mono<ResponseEntity<Object>> result = loadBalancerService.forwardRequest(request);

        // Then
        ResponseEntity<Object> response = result.block();
        assertNotNull(response);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertTrue(response.getBody().toString().contains("No healthy servers available"));
    }
}