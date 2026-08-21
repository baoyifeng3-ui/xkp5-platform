package com.match.registry.service;

import com.match.agent.persistence.ProcessingAgentCommandMapper;
import com.match.agent.persistence.ProcessingAgentCommandRecord;
import org.junit.Test;
import java.util.Arrays;
import static org.mockito.Mockito.*;

public class ImageDeploymentRecoveryTest {
    @Test
    public void oneRecoveryFailureDoesNotStarveLaterCommands() {
        ProcessingAgentCommandMapper commands = mock(ProcessingAgentCommandMapper.class);
        ImageDeploymentService deployments = mock(ImageDeploymentService.class);
        ProcessingAgentCommandRecord first = command("one");
        ProcessingAgentCommandRecord second = command("two");
        when(commands.selectTerminalImageCommands(100)).thenReturn(Arrays.asList(first, second));
        doThrow(new IllegalStateException("bad row")).when(deployments)
                .recoverTerminalCommand(eq("one"), eq("FAILED"), any(), any());
        new ImageDeploymentRecovery(commands, deployments).recover();
        verify(deployments).recoverTerminalCommand(eq("two"), eq("FAILED"), any(), any());
    }

    private ProcessingAgentCommandRecord command(String id) {
        ProcessingAgentCommandRecord c = new ProcessingAgentCommandRecord();
        c.setCommandId(id); c.setState("FAILED"); return c;
    }
}
