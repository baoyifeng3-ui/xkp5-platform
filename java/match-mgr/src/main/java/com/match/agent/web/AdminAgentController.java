package com.match.agent.web;

import com.match.agent.service.AgentQueryService;
import com.match.agent.service.AgentPowerService;
import com.match.agent.service.AgentCommandService;
import com.match.entity.User;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.Instant;

@RestController
@RequestMapping("/admin/processing-agents")
public class AdminAgentController {
    private final RoleGuard roleGuard;
    private final AgentQueryService queryService;
    private final AgentPowerService powerService;
    private final AgentCommandService commandService;

    public AdminAgentController(RoleGuard roleGuard, AgentQueryService queryService,
                                AgentPowerService powerService, AgentCommandService commandService) {
        this.roleGuard = roleGuard;
        this.queryService = queryService;
        this.powerService = powerService;
        this.commandService = commandService;
    }

    @GetMapping
    public ResponseResult<Object> list() {
        roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(queryService.list());
    }

    @GetMapping("/{agentId}")
    public ResponseResult<Object> detail(@PathVariable String agentId) {
        roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(queryService.detail(agentId));
    }

    @GetMapping("/{agentId}/metrics")
    public ResponseResult<Object> history(@PathVariable String agentId,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(queryService.history(agentId, from, to));
    }

    @GetMapping("/{agentId}/commands")
    public ResponseResult<Object> commands(@PathVariable String agentId) {
        roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(commandService.recent(agentId));
    }

    @PostMapping("/{agentId}/wake")
    public ResponseResult<Object> wake(@PathVariable String agentId) {
        User actor = roleGuard.requireAnyAdmin();
        powerService.wake(agentId, actor, roleGuard.roleOf(actor));
        return Response.makeOKRsp(null);
    }

    @PostMapping("/{agentId}/shutdown")
    public ResponseResult<Object> shutdown(@PathVariable String agentId) {
        User actor = roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(powerService.shutdown(agentId, actor, roleGuard.roleOf(actor)));
    }
}
