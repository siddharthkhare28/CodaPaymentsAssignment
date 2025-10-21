package com.codapayments.roundRobin.controller;

import com.codapayments.roundRobin.controller.properties.RoundRobinProperties;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/")
public class RoundRobinController {

    // ✅ List of backend servers
    private final List<String> servers;

    private final AtomicInteger counter = new AtomicInteger(0);
    private final WebClient webClient = WebClient.create();

    // ✅ Health & performance metrics
    private final Map<String, Boolean> healthStatus = new ConcurrentHashMap<>();
    private final Map<String, Long> avgResponseTime = new ConcurrentHashMap<>(); // ms

    // Thresholds (tune for your environment)
    private static final long SLOW_THRESHOLD_MS = 1000;  // 1 second = considered slow
    private static final long INITIAL_LATENCY_MS = 200;  // start assumption for new server

    public RoundRobinController(RoundRobinProperties properties) {
        this.servers = properties.getServers();
    }

    @PostConstruct
    public void initHealth() {
        servers.forEach(s -> {
            healthStatus.put(s, true);
            avgResponseTime.put(s, INITIAL_LATENCY_MS);
        });
    }

    // ✅ Periodic health check every 10s
    @Scheduled(fixedDelay = 10_000)
    public void healthCheck() {
        servers.forEach(server -> {
            webClient.get()
                    .uri(server + "/actuator/health")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(3))
                    .map(resp -> "UP".equalsIgnoreCase(String.valueOf(resp.get("status"))))
                    .onErrorReturn(false)
                    .subscribe(isHealthy -> {
                        healthStatus.put(server, isHealthy);
                        System.out.println("Health of " + server + ": " + (isHealthy ? "UP" : "DOWN"));
                    });
        });
    }

    // ✅ Main proxy endpoint
    @RequestMapping("/**")
    public <T> Mono<ResponseEntity<T>> forwardRequest(
            @RequestBody(required = false) T body,
            @RequestHeader Map<String, String> headers,
            @RequestParam Map<String, String> params,
            HttpMethod method,
            HttpServletRequest request
    ) {
        String path = request.getRequestURI();
        return tryForwardRequest(path, method, headers, params, body, 0);
    }

    // ✅ Recursive retry logic
    private <T> Mono<ResponseEntity<T>> tryForwardRequest(
            String path,
            HttpMethod method,
            Map<String, String> headers,
            Map<String, String> params,
            T body,
            int attempt
    ) {
        if (attempt >= servers.size()) {
            return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body((T) "All backend servers are unavailable"));
        }

        String target = nextHealthyServer();
        if (target == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body((T) "No healthy servers available"));
        }

        String targetUrl = target + path;

        WebClient.RequestBodySpec req = webClient
                .method(method)
                .uri(targetUrl)
                .headers(h -> headers.forEach(h::add));

        Instant start = Instant.now();

        return (body != null ? req.bodyValue(body) : req)
                .retrieve()
                .toEntity(Object.class)
                .timeout(Duration.ofSeconds(5))
                .map(resp -> {
                    long elapsed = Duration.between(start, Instant.now()).toMillis();
                    updateLatency(target, elapsed);
                    System.out.printf("✅ %s responded in %d ms%n", target, elapsed);

                    // Mark slow servers unhealthy temporarily
                    if (elapsed > SLOW_THRESHOLD_MS) {
                        System.out.printf("⚠️ %s is slow (%d ms) — marking temporarily unhealthy%n", target, elapsed);
                        healthStatus.put(target, false);
                    }

                    return ResponseEntity
                            .status(resp.getStatusCode())
                            .headers(resp.getHeaders())
                            .body((T) resp.getBody());
                })
                .onErrorResume(e -> {
                    System.out.printf("❌ %s failed: %s%n", target, e.getMessage());
                    healthStatus.put(target, false);
                    return tryForwardRequest(path, method, headers, params, body, attempt + 1);
                });
    }

    // ✅ Weighted round robin: prefers faster healthy servers
    private String nextHealthyServer() {
        // Filter only healthy servers
        List<String> healthy = servers.stream()
                .filter(s -> healthStatus.getOrDefault(s, false))
                .toList();

        if (healthy.isEmpty()) return null;

        // Find the server with the lowest average latency
//        String fastest = healthy.stream()
//                .min(Comparator.comparingLong(s -> avgResponseTime.getOrDefault(s, INITIAL_LATENCY_MS)))
//                .orElse(null);

        // Occasionally rotate to keep load distribution fair
//        if (counter.incrementAndGet() % healthy.size() == 0)
//            counter.set(0);
        int i = Math.abs(counter.getAndIncrement() % healthy.size());
        return servers.get(i);
    }

    // ✅ Track average response time per backend (simple moving average)
    private void updateLatency(String server, long newLatency) {
        avgResponseTime.compute(server, (s, old) -> {
            if (old == null) return newLatency;
            return (old * 4 + newLatency) / 5; // weighted average
        });
    }
}

