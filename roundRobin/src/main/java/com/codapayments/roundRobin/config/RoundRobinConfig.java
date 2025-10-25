package com.codapayments.roundRobin.config;

import com.codapayments.roundRobin.service.LoadBalancingStrategy;
import com.codapayments.roundRobin.service.ServerDiscoveryService;
import com.codapayments.roundRobin.service.impl.RoundRobinStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class RoundRobinConfig {

    private static final Logger logger = LoggerFactory.getLogger(RoundRobinConfig.class);

    @Bean
    @Primary
    public LoadBalancingStrategy loadBalancingStrategy() {
        // You can make this configurable later
        return new RoundRobinStrategy();
    }

    @Bean
    @Primary
    public ServerDiscoveryService serverDiscoveryService(
            RoundRobinProperties properties,
            @Qualifier("staticServerDiscovery") ServerDiscoveryService staticDiscovery,
            @Qualifier("fileServerDiscovery") ServerDiscoveryService fileDiscovery) {
        
        String strategy = properties.getServerDiscoveryStrategy();
        logger.info("Using server discovery strategy: {}", strategy);
        
        return switch (strategy.toLowerCase()) {
            case "file" -> {
                logger.info("Configured file-based server discovery with path: {}", 
                    properties.getServerDiscoveryFilePath());
                yield fileDiscovery;
            }
            case "static" -> {
                logger.info("Configured static server discovery with {} servers", 
                    properties.getServers() != null ? properties.getServers().size() : 0);
                yield staticDiscovery;
            }
            default -> {
                logger.warn("Unknown server discovery strategy '{}', falling back to static", strategy);
                yield staticDiscovery;
            }
        };
    }
}