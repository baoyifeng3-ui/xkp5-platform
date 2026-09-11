package com.match.registry.web;

import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentCredentialService;
import com.match.registry.service.AgentImageArchiveService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent/v1/image-deployments")
public class AgentImageDeploymentController {
    private final AgentCredentialService credentials;
    private final AgentImageArchiveService archives;

    public AgentImageDeploymentController(AgentCredentialService credentials, AgentImageArchiveService archives) {
        this.credentials = credentials;
        this.archives = archives;
    }

    @GetMapping("/{deploymentId}/archive")
    public ResponseEntity<Void> archive(@RequestHeader("Authorization") String authorization,
                                        @PathVariable String deploymentId) {
        ProcessingAgentRecord agent = credentials.authenticate(authorization);
        return ResponseEntity.ok()
                .header("X-Accel-Redirect", archives.internalLocation(agent.getAgentId(), deploymentId))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=image.tar")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .build();
    }
}
