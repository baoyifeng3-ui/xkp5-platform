package com.match.terminal.web;

import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentCredentialService;
import com.match.terminal.model.AgentTerminalTicketRequest;
import com.match.terminal.model.TerminalTicketView;
import com.match.terminal.service.TerminalSessionService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent/v1")
public class AgentTerminalController {
    private final AgentCredentialService credentialService;
    private final TerminalSessionService service;

    public AgentTerminalController(AgentCredentialService credentialService,
                                   TerminalSessionService service) {
        this.credentialService = credentialService;
        this.service = service;
    }

    @PostMapping("/terminal-sessions/{sessionId}/agent-ticket")
    public TerminalTicketView agentTicket(@RequestHeader("Authorization") String authorization,
                                          @PathVariable String sessionId,
                                          @RequestBody AgentTerminalTicketRequest request) {
        ProcessingAgentRecord agent = credentialService.authenticate(authorization);
        return service.issueAgentTicket(agent, sessionId,
                request == null ? null : request.getCommandId(),
                request == null ? null : request.getLeaseToken());
    }
}
