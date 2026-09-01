package com.match.terminal.web;

import com.match.entity.User;
import com.match.security.RoleGuard;
import com.match.terminal.model.CreateTerminalSessionRequest;
import com.match.terminal.model.TerminalPollingInputRequest;
import com.match.terminal.model.TerminalPollingOutputView;
import com.match.terminal.service.TerminalSessionService;
import com.match.terminal.service.TerminalPollingService;
import com.match.terminal.service.SshBridgeService;
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
    private TerminalPollingService polling;
    private SshBridgeService ssh;

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

    @org.springframework.beans.factory.annotation.Autowired
    public void setPolling(TerminalPollingService polling) { this.polling = polling; }
    @org.springframework.beans.factory.annotation.Autowired public void setSsh(SshBridgeService ssh){this.ssh=ssh;}

    @PostMapping("/processing-agents/{agentId}/ssh-sessions") public ResponseResult<Object> createSsh(@PathVariable("agentId")String agentId){roleGuard.requireSuperAdmin();return Response.makeOKRsp(ssh.create(agentId));}
    @GetMapping("/ssh-sessions/{id}/output") public ResponseResult<Object> sshOutput(@PathVariable("id")String id,@org.springframework.web.bind.annotation.RequestParam(defaultValue="0")long cursor){roleGuard.requireSuperAdmin();return Response.makeOKRsp(ssh.output(id,cursor));}
    @PostMapping("/ssh-sessions/{id}/input") public ResponseResult<Object> sshInput(@PathVariable("id")String id,@RequestBody TerminalPollingInputRequest request){roleGuard.requireSuperAdmin();ssh.input(id,request.getData());return Response.makeOKRsp(null);}
    @DeleteMapping("/ssh-sessions/{id}") public ResponseResult<Object> closeSsh(@PathVariable("id")String id){roleGuard.requireSuperAdmin();ssh.close(id);return Response.makeOKRsp(null);}

    @GetMapping("/terminal-sessions/{sessionId}/output")
    public ResponseResult<TerminalPollingOutputView> output(@PathVariable("sessionId") String sessionId,
                                                             @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") long cursor) {
        roleGuard.requireSuperAdmin();
        return Response.makeOKRsp(polling.output(sessionId, cursor));
    }

    @PostMapping("/terminal-sessions/{sessionId}/input")
    public ResponseResult<Object> input(@PathVariable("sessionId") String sessionId,
                                        @RequestBody TerminalPollingInputRequest request) {
        User actor = roleGuard.requireSuperAdmin();
        return Response.makeOKRsp(polling.input(sessionId, actor, request));
    }
}
