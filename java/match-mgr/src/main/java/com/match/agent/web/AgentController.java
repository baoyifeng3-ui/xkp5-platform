package com.match.agent.web;

import com.match.agent.model.AgentCommandPollResponse;
import com.match.agent.model.AgentCommandResultRequest;
import com.match.agent.model.AgentCommandStartRequest;
import com.match.agent.model.AgentCommandView;
import com.match.agent.model.AgentHeartbeatAck;
import com.match.agent.model.AgentHeartbeatRequest;
import com.match.agent.model.AgentRegistrationRequest;
import com.match.agent.model.AgentRegistrationResponse;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentCredentialService;
import com.match.agent.service.AgentHeartbeatService;
import com.match.agent.service.AgentRegistrationService;
import com.match.agent.service.AgentCommandPoller;
import com.match.agent.service.AgentCommandService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent/v1")
public class AgentController {
    private final AgentRegistrationService registrationService;
    private final AgentCredentialService credentialService;
    private final AgentHeartbeatService heartbeatService;
    private final AgentCommandPoller commandPoller;
    private final AgentCommandService commandService;

    public AgentController(AgentRegistrationService registrationService,
                           AgentCredentialService credentialService,
                           AgentHeartbeatService heartbeatService,
                           AgentCommandPoller commandPoller,
                           AgentCommandService commandService) {
        this.registrationService = registrationService;
        this.credentialService = credentialService;
        this.heartbeatService = heartbeatService;
        this.commandPoller = commandPoller;
        this.commandService = commandService;
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
        if (waitSeconds < 0 || waitSeconds > 25) {
            throw new AgentProtocolException("INVALID_POLL_WAIT", "轮询等待时间必须在 0 到 25 秒之间",
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        ProcessingAgentRecord agent = credentialService.authenticate(authorization);
        return commandPoller.poll(agent, waitSeconds);
    }

    @PostMapping("/commands/{commandId}/start")
    public AgentCommandView startCommand(@RequestHeader("Authorization") String authorization,
                                         @PathVariable String commandId,
                                         @RequestBody AgentCommandStartRequest request) {
        ProcessingAgentRecord agent = credentialService.authenticate(authorization);
        return commandService.start(agent, commandId, request);
    }

    @PostMapping("/commands/{commandId}/result")
    public AgentCommandView finishCommand(@RequestHeader("Authorization") String authorization,
                                          @PathVariable String commandId,
                                          @RequestBody AgentCommandResultRequest request) {
        ProcessingAgentRecord agent = credentialService.authenticate(authorization);
        return commandService.finish(agent, commandId, request);
    }
}
