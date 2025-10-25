package com.codapayments.roundRobin.controller;

import com.codapayments.roundRobin.service.LoadBalancerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoundRobinControllerTest {

    @Mock
    private LoadBalancerService loadBalancerService;

    private RoundRobinController controller;

    @BeforeEach
    void setUp() {
        controller = new RoundRobinController(loadBalancerService);
    }

    @Test
    void shouldForwardRequestToLoadBalancerService() {
        // Given
        String requestBody = "{\"test\": \"data\"}";
        Map<String, String> headers = Map.of("Authorization", "Bearer token");
        Map<String, String> params = Map.of("param1", "value1");
        HttpMethod method = HttpMethod.POST;
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        
        ResponseEntity<Object> expectedResponse = ResponseEntity.ok("Success");
        when(loadBalancerService.forwardRequest(any()))
                .thenReturn(Mono.just(expectedResponse));

        // When
        Mono<ResponseEntity<String>> result = controller.forwardRequest(
                requestBody, headers, params, method, request);

        // Then
        assertNotNull(result.block());
        verify(loadBalancerService).forwardRequest(any());
    }

    @Test
    void shouldSkipAdminEndpoints() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/admin/health");
        
        // When
        Mono<ResponseEntity<String>> result = controller.forwardRequest(
                null, Map.of(), Map.of(), HttpMethod.GET, request);

        // Then
        assertNull(result.block());
        verify(loadBalancerService, never()).forwardRequest(any());
    }

    @Test
    void shouldSkipAllAdminSubPaths() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/admin/stats/detailed");
        
        // When
        Mono<ResponseEntity<String>> result = controller.forwardRequest(
                null, Map.of(), Map.of(), HttpMethod.GET, request);

        // Then
        assertNull(result.block());
        verify(loadBalancerService, never()).forwardRequest(any());
    }

    @Test
    void shouldForwardNonAdminEndpoints() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/users");
        
        ResponseEntity<Object> expectedResponse = ResponseEntity.ok("User data");
        when(loadBalancerService.forwardRequest(any()))
                .thenReturn(Mono.just(expectedResponse));

        // When
        Mono<ResponseEntity<String>> result = controller.forwardRequest(
                null, Map.of(), Map.of(), HttpMethod.GET, request);

        // Then
        assertNotNull(result.block());
        verify(loadBalancerService).forwardRequest(any());
    }

    @Test
    void shouldHandleRootPath() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/");
        
        ResponseEntity<Object> expectedResponse = ResponseEntity.ok("Root response");
        when(loadBalancerService.forwardRequest(any()))
                .thenReturn(Mono.just(expectedResponse));

        // When
        Mono<ResponseEntity<String>> result = controller.forwardRequest(
                null, Map.of(), Map.of(), HttpMethod.GET, request);

        // Then
        assertNotNull(result.block());
        verify(loadBalancerService).forwardRequest(any());
    }

    @Test
    void shouldHandleDifferentHttpMethods() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        
        ResponseEntity<Object> expectedResponse = ResponseEntity.ok("Success");
        when(loadBalancerService.forwardRequest(any()))
                .thenReturn(Mono.just(expectedResponse));

        // Test different HTTP methods
        HttpMethod[] methods = {HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, 
                               HttpMethod.DELETE, HttpMethod.PATCH};

        for (HttpMethod method : methods) {
            // When
            Mono<ResponseEntity<String>> result = controller.forwardRequest(
                    null, Map.of(), Map.of(), method, request);

            // Then
            assertNotNull(result.block());
        }

        verify(loadBalancerService, times(methods.length)).forwardRequest(any());
    }
}