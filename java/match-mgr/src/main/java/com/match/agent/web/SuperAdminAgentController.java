package com.match.agent.web;

import com.match.agent.model.RegistrationTokenRequest;
import com.match.agent.service.RegistrationTokenService;
import com.match.agent.service.AgentAdministrationService;
import com.match.agent.service.AgentPackageService;
import com.match.agent.service.AgentConnectivityService;
import com.match.agent.model.AgentPackageRequest;
import com.match.entity.User;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping("/super-admin/processing-agents")
public class SuperAdminAgentController {
    private final RoleGuard roleGuard;
    private final RegistrationTokenService tokenService;
    private final AgentAdministrationService administrationService;
    private final AgentPackageService packageService;
    private final AgentConnectivityService connectivityService;

    public SuperAdminAgentController(RoleGuard roleGuard, RegistrationTokenService tokenService,
                                     AgentAdministrationService administrationService) {
        this(roleGuard, tokenService, administrationService, null, null);
    }

    @Autowired
    public SuperAdminAgentController(RoleGuard roleGuard, RegistrationTokenService tokenService,
                                     AgentAdministrationService administrationService,
                                     AgentPackageService packageService,
                                     AgentConnectivityService connectivityService) {
        this.roleGuard = roleGuard;
        this.tokenService = tokenService;
        this.administrationService = administrationService;
        this.packageService = packageService;
        this.connectivityService = connectivityService;
    }

    @GetMapping("/connectivity")
    public ResponseResult<Object> connectivity(@org.springframework.web.bind.annotation.RequestParam String serverIp) {
        roleGuard.requireSuperAdmin();
        return Response.makeOKRsp(connectivityService.check(serverIp));
    }

    @PostMapping("/package")
    public ResponseEntity<byte[]> packageAgent(@RequestBody AgentPackageRequest request) {
        User actor = roleGuard.requireSuperAdmin();
        AgentPackageService.PackageArtifact artifact = packageService.build(actor.getUserId(), request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifact.getFileName() + "\"")
                .contentType(MediaType.parseMediaType("application/gzip"))
                .body(artifact.getContent());
    }

    @PostMapping("/registration-tokens")
    public ResponseResult<Object> createToken(@RequestBody(required = false) RegistrationTokenRequest request) {
        User actor = roleGuard.requireSuperAdmin();
        String label = request == null ? null : request.getLabel();
        return Response.makeOKRsp(tokenService.create(actor.getUserId(), label));
    }

    @GetMapping("/registration-tokens")
    public ResponseResult<Object> registrationTokens() {
        roleGuard.requireSuperAdmin();
        return Response.makeOKRsp(tokenService.list());
    }

    @PostMapping("/{agentId}/enable")
    public ResponseResult<Object> enable(@PathVariable String agentId) {
        User actor = roleGuard.requireSuperAdmin();
        administrationService.setEnabled(agentId, true, actor.getUserId());
        return Response.makeOKRsp(null);
    }

    @PostMapping("/{agentId}/disable")
    public ResponseResult<Object> disable(@PathVariable String agentId) {
        User actor = roleGuard.requireSuperAdmin();
        administrationService.setEnabled(agentId, false, actor.getUserId());
        return Response.makeOKRsp(null);
    }

    @DeleteMapping("/{agentId}")
    public ResponseResult<Object> remove(@PathVariable String agentId) {
        User actor = roleGuard.requireSuperAdmin();
        administrationService.remove(agentId, actor.getUserId());
        return Response.makeOKRsp(null);
    }
}
