package com.match.config;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class WebConfigAgentBoundaryTest {
    @Test
    public void agentProtocolBypassesBrowserSessionInterceptor() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/match/config/WebConfig.java")), StandardCharsets.UTF_8);
        assertTrue(source.contains("\"/agent/v1/**\""));
        assertTrue(source.contains("\"/terminal/v1/**\""));
    }
}
