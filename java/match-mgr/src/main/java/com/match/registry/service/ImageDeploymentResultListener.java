package com.match.registry.service;

import com.match.agent.model.AgentCommandFinishedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Component;

@Component
public class ImageDeploymentResultListener {
    private final ImageDeploymentService deployments;
    public ImageDeploymentResultListener(ImageDeploymentService deployments) { this.deployments = deployments; }
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFinished(AgentCommandFinishedEvent event) { deployments.reconcile(event); }
}
