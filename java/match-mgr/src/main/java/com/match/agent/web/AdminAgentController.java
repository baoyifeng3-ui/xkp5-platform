package com.match.agent.web;

import com.match.agent.service.AgentQueryService;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/admin/processing-agents")
public class AdminAgentController {
    private final RoleGuard roleGuard;
    private final AgentQueryService queryService;

    public AdminAgentController(RoleGuard roleGuard, AgentQueryService queryService) {
        this.roleGuard = roleGuard;
        this.queryService = queryService;
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
}
