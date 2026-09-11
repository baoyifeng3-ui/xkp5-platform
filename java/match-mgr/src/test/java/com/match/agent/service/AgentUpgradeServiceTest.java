package com.match.agent.service;

import com.match.agent.persistence.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AgentUpgradeServiceTest {
    @Test public void onlyStrictlyNewerStableVersionsUpgrade() {
        assertTrue(AgentUpgradeService.isNewer("0.2.9", "0.2.31"));
        assertFalse(AgentUpgradeService.isNewer("0.2.31", "0.2.31"));
        assertFalse(AgentUpgradeService.isNewer("0.3.0", "0.2.31"));
        assertFalse(AgentUpgradeService.isNewer(null, "0.2.31"));
        assertFalse(AgentUpgradeService.isNewer("unknown", "0.2.31"));
    }
    @Test public void sameVersionCannotDispatchOrReadBinary() {
        ProcessingAgentMapper agents = mock(ProcessingAgentMapper.class);
        AgentCommandService commands = mock(AgentCommandService.class);
        AgentPackageService packages = mock(AgentPackageService.class);
        ProcessingAgentRecord record = new ProcessingAgentRecord();
        record.setEnabled(true); record.setAgentVersion(AgentPackageService.AGENT_VERSION);
        when(agents.selectForManagement("a")).thenReturn(record);
        try { new AgentUpgradeService(agents, commands, packages).upgrade("a", 1); fail("same version accepted"); }
        catch (IllegalArgumentException expected) { }
        verifyZeroInteractions(commands, packages);
    }
}
