package com.codapayments.roundRobin.service;

import com.codapayments.roundRobin.config.RoundRobinProperties;
import com.codapayments.roundRobin.model.ProxyRequest;
import com.codapayments.roundRobin.model.ServerHealth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Service
public class LoadBalancerService {

    private static final Logger logger = LoggerFactory.getLogger(LoadBalancerService.class);
    
    private final WebClient webClient;
    private final HealthCheckService healthCheckService;
    private final LoadBalancingStrategy loadBalancingStrategy;
    private final RoundRobinProperties properties;

    public LoadBalancerService(WebClient.Builder webClientBuilder, 
                             HealthCheckService healthCheckService,
                             LoadBalancingStrategy loadBalancingStrategy,
                             RoundRobinProperties properties) {
        this.webClient = webClientBuilder.build();
        this.healthCheckService = healthCheckService;
        this.loadBalancingStrategy = loadBalancingStrategy;
        this.properties = properties;
    }

    public Mono<ResponseEntity<Object>> forwardRequest(ProxyRequest<Object> request) {
        return tryForwardRequest(request, 0);
    }

    private Mono<ResponseEntity<Object>> tryForwardRequest(ProxyRequest<Object> request, int attempt) {
        List<ServerHealth> healthyServers = healthCheckService.getHealthyServers();
        
        if (attempt >= healthyServers.size()) {
            logger.warn("All servers exhausted after {} attempts for {}", attempt, request.getPath());
            return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("All backend servers are unavailable"));
        }

        ServerHealth selectedServer = loadBalancingStrategy.selectServer(healthyServers);
        if (selectedServer == null) {
            logger.warn("No healthy servers available for {}", request.getPath());
            return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("No healthy servers available"));
        }

        String targetUrl = selectedServer.getServerUrl() + request.getPath();
        logger.debug("Forwarding {} {} to {}", request.getMethod(), request.getPath(), targetUrl);

        return executeRequest(request, selectedServer, targetUrl, attempt);
    }

    private Mono<ResponseEntity<Object>> executeRequest(ProxyRequest<Object> request, 
                                                     ServerHealth selectedServer, 
                                                     String targetUrl, 
                                                     int attempt) {
        // Build URL with query parameters
        StringBuilder urlBuilder = new StringBuilder(targetUrl);
        if (request.getQueryParams() != null && !request.getQueryParams().isEmpty()) {
            urlBuilder.append("?");
            request.getQueryParams().forEach((key, value) -> 
                urlBuilder.append(key).append("=").append(value).append("&"));
            // Remove the trailing &
            urlBuilder.setLength(urlBuilder.length() - 1);
        }
        
        String finalUrl = urlBuilder.toString();
        logger.debug("Final URL with query params: {}", finalUrl);
        
        WebClient.RequestBodySpec requestSpec = webClient
                .method(request.getMethod())
                .uri(finalUrl)
                .headers(headers -> headers.addAll(request.getHeaders()));

        Instant startTime = Instant.now();

        Mono<ResponseEntity<Object>> responseMono = (request.getBody() != null ? 
                requestSpec.bodyValue(request.getBody()) : requestSpec)
                .retrieve()
                .toEntity(Object.class)
                .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()));

        return responseMono
                .map(response -> {
                    long responseTime = Duration.between(startTime, Instant.now()).toMillis();
                    healthCheckService.updateServerResponseTime(selectedServer.getServerUrl(), responseTime);
                    
                    logger.info("✅ {} responded in {}ms for {} {}", 
                            selectedServer.getServerUrl(), responseTime, request.getMethod(), request.getPath());
                    
                    return ResponseEntity
                            .status(response.getStatusCode())
                            .headers(response.getHeaders())
                            .body(response.getBody());
                })
                .onErrorResume(error -> {
                    long responseTime = Duration.between(startTime, Instant.now()).toMillis();
                    
                    // Only mark server unhealthy for connection/network errors, not HTTP status errors
                    if (isServerDownError(error)) {
                        logger.error("❌ {} is unreachable after {}ms for {} {}: {}", 
                                selectedServer.getServerUrl(), responseTime, 
                                request.getMethod(), request.getPath(), error.getMessage());
                        
                        healthCheckService.markServerUnhealthy(selectedServer.getServerUrl(), 
                                "Server unreachable: " + error.getMessage());
                        
                        // Retry with next server only if server is down
                        return tryForwardRequest(request, attempt + 1);
                    } else {
                        // HTTP status errors (4xx, 5xx) - server is responding, just return the error
                        logger.warn("⚠️ {} returned error after {}ms for {} {}: {}", 
                                selectedServer.getServerUrl(), responseTime, 
                                request.getMethod(), request.getPath(), error.getMessage());
                        
                        // Still update response time since server responded
                        healthCheckService.updateServerResponseTime(selectedServer.getServerUrl(), responseTime);
                        
                        // Return the actual HTTP error response without retrying
                        return Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                                .body("Backend server error: " + error.getMessage()));
                    }
                });
    }

    public List<ServerHealth> getServerStatuses() {
        return healthCheckService.getAllServers();
    }

    public String getLoadBalancingStrategy() {
        return loadBalancingStrategy.getStrategyName();
    }
    
    /**
     * Determines if an error indicates the server is down/unreachable (vs HTTP status errors)
     */
    private boolean isServerDownError(Throwable error) {
        // Check if it's a WebClientResponseException (HTTP status codes like 4xx, 5xx)
        if (error instanceof WebClientResponseException) {
            // These are HTTP responses, server is responding - don't mark as unhealthy
            return false;
        }
        
        // Check for connection/network errors that indicate server is down
        if (error instanceof WebClientRequestException ||
            error instanceof ConnectException ||
            error instanceof UnknownHostException ||
            error instanceof TimeoutException ||
            error instanceof PrematureCloseException) {
            return true;
        }
        
        // Check causes for nested connection errors
        Throwable cause = error.getCause();
        if (cause != null) {
            return isServerDownError(cause);
        }
        
        // For any other unknown errors, be conservative and assume server might be down
        return true;
    }
}