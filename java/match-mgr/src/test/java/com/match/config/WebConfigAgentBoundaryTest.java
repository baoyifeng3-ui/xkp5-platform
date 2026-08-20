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
import static org.junit.Assert.assertTrue;

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

    @Test
    public void participantModeGuardsPreserveExactProtectedBoundaries() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/match/config/WebConfig.java")), StandardCharsets.UTF_8);

        assertEquals(1, occurrences(source, "participantModeGuard::requireCurrentGeneration"));
        assertEquals(1, occurrences(source, "\"/user/training-environments/**\""));
        assertEquals(1, occurrences(source, "participantModeGuard::requireTrainingMode"));
        assertEquals(1, occurrences(source, "\"/testPaper/**\""));
        assertEquals(1, occurrences(source, "\"/score/**\""));
        assertEquals(1, occurrences(source, "\"/train-url/**\""));
        assertEquals(2, occurrences(source, "participantModeGuard::requireCompetitionMode"));
        assertEquals(1, occurrences(source, "\"/user/competition-environment/**\""));
        assertEquals(1, occurrences(source, "\"/admin/training-environments/**\""));
        assertEquals(1, occurrences(source, "\"/super-admin/training-environments/**\""));
        assertEquals(1, occurrences(source,
                "participantModeGuard::requireAdministrativeTrainingMode"));
        assertTrue(source.indexOf("StpUtil.checkLogin()")
                < source.indexOf("participantModeGuard::requireCurrentGeneration"));
    }

    private int occurrences(String source, String value) {
        return (source.length() - source.replace(value, "").length()) / value.length();
    }
}
