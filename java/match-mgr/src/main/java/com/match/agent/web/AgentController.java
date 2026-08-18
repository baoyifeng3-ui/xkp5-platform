package com.match.agent.web;

import com.match.agent.model.AgentCommandPollResponse;
import com.match.agent.model.AgentHeartbeatAck;
import com.match.agent.model.AgentHeartbeatRequest;
import com.match.agent.model.AgentRegistrationRequest;
import com.match.agent.model.AgentRegistrationResponse;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentCredentialService;
import com.match.agent.service.AgentHeartbeatService;
import com.match.agent.service.AgentRegistrationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent/v1")
public class AgentController {
    private final AgentRegistrationService registrationService;
    private final AgentCredentialService credentialService;
    private final AgentHeartbeatService heartbeatService;

    public AgentController(AgentRegistrationService registrationService,
                           AgentCredentialService credentialService,
                           AgentHeartbeatService heartbeatService) {
        this.registrationService = registrationService;
        this.credentialService = credentialService;
        this.heartbeatService = heartbeatService;
    }

    @PostMapping("/register")
    public AgentRegistrationResponse register(@RequestBody AgentRegistrationRequest request) {
        return registrationService.register(request);
    }

    @PostMapping("/heartbeat")
    public AgentHeartbeatAck heartbeat(@RequestHeader("Authorization") String authorization,
                                       @RequestBody AgentHeartbeatRequest request) {
        ProcessingAgentRecord agent = credentialService.authenticate(authorization);
        return heartbeatService.accept(agent, request);
    }

    @GetMapping("/commands/poll")
    public AgentCommandPollResponse pollCommands(@RequestHeader("Authorization") String authorization,
                                                  @RequestParam(defaultValue = "25") int waitSeconds) {
        credentialService.authenticate(authorization);
        if (waitSeconds < 0 || waitSeconds > 25) {
            throw new AgentProtocolException("INVALID_POLL_WAIT", "轮询等待时间必须在 0 到 25 秒之间",
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        return new AgentCommandPollResponse();
    }
}
