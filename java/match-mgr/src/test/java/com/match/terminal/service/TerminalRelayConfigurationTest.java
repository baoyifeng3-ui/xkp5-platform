package com.match.terminal.service;

import org.junit.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.PropertyPlaceholderHelper;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class TerminalRelayConfigurationTest {
    @Test
    public void productionRequiresExplicitNonLoopbackRelayEnvironmentVariable() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-prod.yml"));
        Properties properties = yaml.getObject();
        String configured = properties.getProperty("match.terminal.agent-relay-url");

        assertEquals("${TERMINAL_AGENT_RELAY_URL}", configured);
        assertFalse(configured.contains("127.0.0.1"));
        assertFalse(configured.contains("localhost"));
        PropertyPlaceholderHelper resolver = new PropertyPlaceholderHelper(
                "${", "}", ":", false);
        try {
            resolver.replacePlaceholders(configured, placeholder -> null);
        } catch (IllegalArgumentException expected) {
            return;
        }
        fail("missing production TERMINAL_AGENT_RELAY_URL was accepted");
    }
}
