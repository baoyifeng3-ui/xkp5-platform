package com.match.account.web;

import com.match.account.model.AccountMigrationRequest;
import com.match.account.service.AccountEnvironmentMigrationService;
import com.match.entity.User;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/admin/account-migrations")
public class AccountMigrationController {
    private final RoleGuard roles;private final AccountEnvironmentMigrationService migrations;
    public AccountMigrationController(RoleGuard roles,AccountEnvironmentMigrationService migrations){this.roles=roles;this.migrations=migrations;}
    @PostMapping("/preflight") public ResponseResult<Object> preflight(@RequestBody AccountMigrationRequest request){User actor=roles.requireBusinessAdmin();return Response.makeOKRsp(migrations.preflight(request,actor.getUserId()));}
}
