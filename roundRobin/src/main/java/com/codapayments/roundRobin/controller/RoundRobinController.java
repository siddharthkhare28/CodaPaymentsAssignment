package com.codapayments.roundRobin.controller;

import com.codapayments.roundRobin.model.ProxyRequest;
import com.codapayments.roundRobin.service.LoadBalancerService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/")
public class RoundRobinController {

    private final LoadBalancerService loadBalancerService;

    public RoundRobinController(LoadBalancerService loadBalancerService) {
        this.loadBalancerService = loadBalancerService;
    }

    /**
     * Main proxy endpoint that forwards all requests to backend servers
     * Excludes admin endpoints
     */
    @RequestMapping("/**")
    public <T> Mono<ResponseEntity<T>> forwardRequest(
            @RequestBody(required = false) T body,
            @RequestHeader Map<String, String> headers,
            @RequestParam Map<String, String> params,
            HttpMethod method,
            HttpServletRequest request
    ) {
        String path = request.getRequestURI();
        
        // Skip admin endpoints
        if (path.startsWith("/admin/")) {
            return Mono.empty();
        }
        
        ProxyRequest<T> proxyRequest = new ProxyRequest<>(path, method, headers, params, body);
        return loadBalancerService.forwardRequest(proxyRequest);
    }
}

