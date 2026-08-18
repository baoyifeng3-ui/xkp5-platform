package com.match.agent.web;

import com.match.agent.model.AgentRegistrationRequest;
import com.match.agent.model.AgentRegistrationResponse;
import com.match.agent.service.AgentRegistrationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent/v1")
public class AgentController {
    private final AgentRegistrationService registrationService;

    public AgentController(AgentRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    public AgentRegistrationResponse register(@RequestBody AgentRegistrationRequest request) {
        return registrationService.register(request);
    }
}
