package com.match.registry.service;

import com.match.agent.persistence.ProcessingAgentCommandMapper;
import com.match.agent.persistence.ProcessingAgentCommandRecord;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ImageDeploymentRecovery {
    private final ProcessingAgentCommandMapper commands;
    private final ImageDeploymentService deployments;
    public ImageDeploymentRecovery(ProcessingAgentCommandMapper commands, ImageDeploymentService deployments) {
        this.commands = commands; this.deployments = deployments;
    }
    @Scheduled(fixedDelayString = "${xkp.image-deployment.recovery-delay-ms:30000}")
    public void recover() {
        for (ProcessingAgentCommandRecord command : commands.selectTerminalImageCommands(100)) {
            deployments.recoverTerminalCommand(command.getCommandId(), command.getState(),
                    command.getResultCode(), command.getResultMessage());
        }
    }
}
