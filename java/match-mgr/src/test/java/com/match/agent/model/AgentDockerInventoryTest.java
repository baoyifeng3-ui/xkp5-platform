package com.match.agent.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class AgentDockerInventoryTest {
    @Test public void heartbeatParsesDockerInventory() throws Exception {
        AgentMetricSnapshot metrics = new ObjectMapper().readValue("{\"images\":[{\"repository\":\"xkp/anno\",\"tag\":\"v1\"}],\"containers\":[{\"name\":\"anno\",\"state\":\"running\"}]}", AgentMetricSnapshot.class);
        assertEquals("xkp/anno", metrics.getImages().get(0).getRepository());
        assertEquals("running", metrics.getContainers().get(0).getState());
    }
}
