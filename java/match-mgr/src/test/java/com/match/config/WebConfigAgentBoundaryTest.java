package com.match.config;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;

public class WebConfigAgentBoundaryTest {
    @Test
    public void agentProtocolBypassesBrowserSessionInterceptor() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/match/config/WebConfig.java")), StandardCharsets.UTF_8);
        int start = source.indexOf(".notMatch(");
        int end = source.indexOf(".check(() ->", start);
        Matcher paths = Pattern.compile("\"([^\"]+)\"").matcher(source.substring(start, end));
        Set<String> actual = new HashSet<>();
        while (paths.find()) {
            actual.add(paths.group(1));
        }
        Set<String> expected = new HashSet<>(Arrays.asList("/user/login", "/competition",
                "/health", "/error", "/agent/v1/**", "/terminal/v1/**", "/v2/api-docs/**",
                "/swagger-resources/**", "/swagger-ui.html"));
        assertEquals(expected, actual);
        assertEquals(1, occurrences(source, "\"/agent/v1/**\""));
        assertEquals(1, occurrences(source, "\"/terminal/v1/**\""));
    }

    private int occurrences(String source, String value) {
        return (source.length() - source.replace(value, "").length()) / value.length();
    }
}
