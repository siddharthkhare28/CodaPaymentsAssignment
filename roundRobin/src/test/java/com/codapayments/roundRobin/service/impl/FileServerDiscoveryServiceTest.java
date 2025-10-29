package com.codapayments.roundRobin.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileServerDiscoveryServiceTest {

    @TempDir
    Path tempDir;

    private FileServerDiscoveryService discoveryService;
    private Path testServerFile;

    @BeforeEach
    void setUp() throws IOException {
        discoveryService = new FileServerDiscoveryService();
        testServerFile = tempDir.resolve("test-servers.txt");
        
        // Set the file path using reflection to avoid Spring dependency
        ReflectionTestUtils.setField(discoveryService, "serverFilePath", testServerFile.toString());
    }

    @Test
    void testGetServers_WithValidServerFile() throws IOException {
        // Given
        String fileContent = """
                http://server1:8080
                http://server2:8080
                http://server3:8080
                """;
        Files.writeString(testServerFile, fileContent);

        // When
        List<String> servers = discoveryService.getServers();

        // Then
        assertNotNull(servers);
        assertEquals(3, servers.size());
        assertTrue(servers.contains("http://server1:8080"));
        assertTrue(servers.contains("http://server2:8080"));
        assertTrue(servers.contains("http://server3:8080"));
    }

    @Test
    void testGetServers_WithEmptyFile() throws IOException {
        // Given
        Files.writeString(testServerFile, "");

        // When
        List<String> servers = discoveryService.getServers();

        // Then
        assertNotNull(servers);
        assertTrue(servers.isEmpty());
    }

    @Test
    void testGetServers_WithCommentsAndEmptyLines() throws IOException {
        // Given
        String fileContent = """
                # This is a comment
                http://server1:8080
                
                # Another comment
                http://server2:8080
                
                http://server3:8080
                # Final comment
                """;
        Files.writeString(testServerFile, fileContent);

        // When
        List<String> servers = discoveryService.getServers();

        // Then
        assertNotNull(servers);
        assertEquals(3, servers.size());
        assertTrue(servers.contains("http://server1:8080"));
        assertTrue(servers.contains("http://server2:8080"));
        assertTrue(servers.contains("http://server3:8080"));
    }

    @Test
    void testGetServers_WithWhitespaceAndTrimming() throws IOException {
        // Given
        String fileContent = """
                  http://server1:8080  
                	http://server2:8080	
                 http://server3:8080 
                """;
        Files.writeString(testServerFile, fileContent);

        // When
        List<String> servers = discoveryService.getServers();

        // Then
        assertNotNull(servers);
        assertEquals(3, servers.size());
        assertEquals("http://server1:8080", servers.get(0));
        assertEquals("http://server2:8080", servers.get(1));
        assertEquals("http://server3:8080", servers.get(2));
    }

    @Test
    void testGetServers_WithMixedProtocols() throws IOException {
        // Given
        String fileContent = """
                http://server1:8080
                https://server2:8443
                http://server3:9090
                https://secure-server:443
                """;
        Files.writeString(testServerFile, fileContent);

        // When
        List<String> servers = discoveryService.getServers();

        // Then
        assertNotNull(servers);
        assertEquals(4, servers.size());
        assertTrue(servers.contains("http://server1:8080"));
        assertTrue(servers.contains("https://server2:8443"));
        assertTrue(servers.contains("http://server3:9090"));
        assertTrue(servers.contains("https://secure-server:443"));
    }

    @Test
    void testGetServers_FileDoesNotExist() {
        // Given - testServerFile doesn't exist

        // When
        List<String> servers = discoveryService.getServers();

        // Then
        assertNotNull(servers);
        assertTrue(servers.isEmpty());
    }

    @Test
    void testGetServers_CachingBehavior() throws IOException {
        // Given
        String fileContent = "http://server1:8080\n";
        Files.writeString(testServerFile, fileContent);

        // When
        List<String> firstCall = discoveryService.getServers();
        List<String> secondCall = discoveryService.getServers();

        // Then
        assertEquals(firstCall, secondCall);
        assertEquals(1, firstCall.size());
        assertEquals("http://server1:8080", firstCall.get(0));
    }

    @Test
    void testGetServers_FileModificationDetection() throws IOException, InterruptedException {
        // Given
        String initialContent = "http://server1:8080\n";
        Files.writeString(testServerFile, initialContent);

        // When - First read
        List<String> initialServers = discoveryService.getServers();

        // Wait a bit to ensure different modification time
        Thread.sleep(100);

        // Modify file
        String updatedContent = """
                http://server1:8080
                http://server2:8080
                """;
        Files.writeString(testServerFile, updatedContent);

        // Read again
        List<String> updatedServers = discoveryService.getServers();

        // Then
        assertEquals(1, initialServers.size());
        assertEquals(2, updatedServers.size());
        assertTrue(updatedServers.contains("http://server1:8080"));
        assertTrue(updatedServers.contains("http://server2:8080"));
    }

    @Test
    void testGetServers_FileAppending() throws IOException, InterruptedException {
        // Given
        String initialContent = "http://server1:8080\n";
        Files.writeString(testServerFile, initialContent);

        // When - First read
        List<String> initialServers = discoveryService.getServers();

        // Wait a bit to ensure different modification time
        Thread.sleep(100);

        // Append to file
        Files.writeString(testServerFile, "http://server2:8080\n", StandardOpenOption.APPEND);

        // Read again
        List<String> updatedServers = discoveryService.getServers();

        // Then
        assertEquals(1, initialServers.size());
        assertEquals(2, updatedServers.size());
        assertTrue(updatedServers.contains("http://server1:8080"));
        assertTrue(updatedServers.contains("http://server2:8080"));
    }

    @Test
    void testGetServers_OnlyComments() throws IOException {
        // Given
        String fileContent = """
                # Comment line 1
                # Comment line 2
                # Comment line 3
                """;
        Files.writeString(testServerFile, fileContent);

        // When
        List<String> servers = discoveryService.getServers();

        // Then
        assertNotNull(servers);
        assertTrue(servers.isEmpty());
    }

    @Test
    void testGetServers_OnlyEmptyLines() throws IOException {
        // Given
        String fileContent = "\n\n\n\n";
        Files.writeString(testServerFile, fileContent);

        // When
        List<String> servers = discoveryService.getServers();

        // Then
        assertNotNull(servers);
        assertTrue(servers.isEmpty());
    }

    @Test
    void testGetServers_ComplexServerNames() throws IOException {
        // Given
        String fileContent = """
                http://api-server-1.prod.example.com:8080
                https://api-server_2.staging.example.com:8443
                http://192.168.1.100:8080
                http://[::1]:8080
                http://localhost:8080
                """;
        Files.writeString(testServerFile, fileContent);

        // When
        List<String> servers = discoveryService.getServers();

        // Then
        assertNotNull(servers);
        assertEquals(5, servers.size());
        assertTrue(servers.contains("http://api-server-1.prod.example.com:8080"));
        assertTrue(servers.contains("https://api-server_2.staging.example.com:8443"));
        assertTrue(servers.contains("http://192.168.1.100:8080"));
        assertTrue(servers.contains("http://[::1]:8080"));
        assertTrue(servers.contains("http://localhost:8080"));
    }

    @Test
    void testGetServers_ReturnedListIsolation() throws IOException {
        // Given
        String fileContent = """
                http://server1:8080
                http://server2:8080
                """;
        Files.writeString(testServerFile, fileContent);

        // When
        List<String> servers1 = discoveryService.getServers();
        List<String> servers2 = discoveryService.getServers();

        // Then - Lists should be independent
        assertNotSame(servers1, servers2);
        assertEquals(servers1, servers2);
        
        // Modifying one list shouldn't affect the other
        if (!servers1.isEmpty()) {
            try {
                servers1.clear(); // This might throw if it's immutable, which is fine
            } catch (UnsupportedOperationException e) {
                // Expected if the list is immutable
            }
            
            // The service should still return the correct data
            List<String> servers3 = discoveryService.getServers();
            assertEquals(2, servers3.size());
        }
    }

    @Test
    void testGetStrategyName() {
        // When
        String strategyName = discoveryService.getStrategyName();

        // Then
        assertEquals("File-based Discovery", strategyName);
        assertNotNull(strategyName);
        assertFalse(strategyName.isEmpty());
    }

    @Test
    void testSupportsDynamicUpdates() {
        // When
        boolean supportsDynamic = discoveryService.supportsDynamicUpdates();

        // Then
        assertTrue(supportsDynamic);
    }

    @Test
    void testGetServers_MultipleConsecutiveCalls() throws IOException {
        // Given
        String fileContent = "http://server1:8080\n";
        Files.writeString(testServerFile, fileContent);

        // When - Multiple consecutive calls
        List<String> call1 = discoveryService.getServers();
        List<String> call2 = discoveryService.getServers();
        List<String> call3 = discoveryService.getServers();

        // Then - All calls should return consistent results
        assertEquals(call1, call2);
        assertEquals(call2, call3);
        assertEquals(1, call1.size());
        assertEquals(1, call2.size());
        assertEquals(1, call3.size());
    }
}