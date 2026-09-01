package com.match.environment.web;

import com.match.entity.User;
import com.match.environment.service.EnvironmentPortPoolService;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/super-admin/processing-agents/{agentId}/port-pools")
public class EnvironmentPortPoolController {
    private final RoleGuard roles;private final EnvironmentPortPoolService pools;
    public EnvironmentPortPoolController(RoleGuard roles,EnvironmentPortPoolService pools){this.roles=roles;this.pools=pools;}
    @GetMapping public ResponseResult<Object> list(@PathVariable String agentId){roles.requireSuperAdmin();return Response.makeOKRsp(pools.list(agentId));}
    @PutMapping("/{poolId}") public ResponseResult<Object> update(@PathVariable String agentId,@PathVariable String poolId,@RequestBody Map<String,Integer> request){User actor=roles.requireSuperAdmin();return Response.makeOKRsp(pools.update(poolId,request.get("rangeStart"),request.get("rangeEnd"),actor.getUserId()));}
}
