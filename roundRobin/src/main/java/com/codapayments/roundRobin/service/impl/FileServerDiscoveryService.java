package com.codapayments.roundRobin.service.impl;

import com.codapayments.roundRobin.service.ServerDiscoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * File-based server discovery that watches a file for changes
 */
@Service("fileServerDiscovery")
public class FileServerDiscoveryService implements ServerDiscoveryService {
    
    private static final Logger logger = LoggerFactory.getLogger(FileServerDiscoveryService.class);
    
    @Value("${roundrobin.server-discovery.file-path:servers.txt}")
    private String serverFilePath;
    
    private final AtomicReference<List<String>> cachedServers = new AtomicReference<>(new ArrayList<>());
    private volatile Instant lastModified = Instant.EPOCH;
    
    @Override
    public List<String> getServers() {
        refreshServersIfNeeded();
        return new ArrayList<>(cachedServers.get());
    }
    
    @Override
    public String getStrategyName() {
        return "File-based Discovery";
    }
    
    @Override
    public boolean supportsDynamicUpdates() {
        return true;
    }
    
    private void refreshServersIfNeeded() {
        try {
            Path path = Paths.get(serverFilePath);
            
            if (!Files.exists(path)) {
                logger.warn("Server file {} does not exist, using empty server list", serverFilePath);
                cachedServers.set(new ArrayList<>());
                return;
            }
            
            Instant fileLastModified = Files.getLastModifiedTime(path).toInstant();
            
            if (fileLastModified.isAfter(lastModified)) {
                logger.info("Server file {} has been modified, reloading servers", serverFilePath);
                loadServersFromFile(path);
                lastModified = fileLastModified;
            }
            
        } catch (IOException e) {
            logger.error("Error checking server file {}: {}", serverFilePath, e.getMessage());
        }
    }
    
    private void loadServersFromFile(Path path) throws IOException {
        List<String> servers = Files.readAllLines(path, StandardCharsets.UTF_8)
                .stream()
                .map(this::removeBOM)
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
        
        logger.info("Loaded {} servers from file {}: {}", servers.size(), serverFilePath, servers);
        cachedServers.set(servers);
    }
    
    private String removeBOM(String line) {
        // Remove UTF-8 BOM if present at the beginning of the line
        if (line.length() > 0 && line.charAt(0) == '\uFEFF') {
            return line.substring(1);
        }
        return line;
    }
}