package com.match.terminal.web;

import com.match.entity.User;
import com.match.security.RoleGuard;
import com.match.terminal.model.CreateTerminalSessionRequest;
import com.match.terminal.service.TerminalSessionService;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/operations")
public class OperationsTerminalController {
    private final RoleGuard roleGuard;
    private final TerminalSessionService service;

    public OperationsTerminalController(RoleGuard roleGuard, TerminalSessionService service) {
        this.roleGuard = roleGuard;
        this.service = service;
    }

    @PostMapping("/processing-agents/{agentId}/terminal-sessions")
    public ResponseResult<Object> create(@PathVariable String agentId,
                                         @RequestBody CreateTerminalSessionRequest request) {
        User actor = roleGuard.requireSuperAdmin();
        return Response.makeOKRsp(service.create(agentId, actor,
                request == null ? null : request.getConfirmation()));
    }

    @GetMapping("/terminal-sessions/{sessionId}")
    public ResponseResult<Object> view(@PathVariable String sessionId) {
        User actor = roleGuard.requireSuperAdmin();
        return Response.makeOKRsp(service.view(sessionId, actor));
    }

    @PostMapping("/terminal-sessions/{sessionId}/browser-ticket")
    public ResponseResult<Object> browserTicket(@PathVariable String sessionId) {
        User actor = roleGuard.requireSuperAdmin();
        return Response.makeOKRsp(service.issueBrowserTicket(sessionId, actor));
    }

    @DeleteMapping("/terminal-sessions/{sessionId}")
    public ResponseResult<Object> close(@PathVariable String sessionId) {
        User actor = roleGuard.requireSuperAdmin();
        service.close(sessionId, actor);
        return Response.makeOKRsp(null);
    }
}
