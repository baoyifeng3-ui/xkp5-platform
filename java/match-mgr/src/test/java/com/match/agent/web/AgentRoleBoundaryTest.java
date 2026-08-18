package com.match.agent.web;

import com.match.agent.service.AgentAdministrationService;
import com.match.agent.service.AgentQueryService;
import com.match.agent.service.RegistrationTokenService;
import com.match.entity.User;
import com.match.security.RoleGuard;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AgentRoleBoundaryTest {
    @Test
    public void normalAdminCanReadButSuperAdminOwnsMutations() {
        RoleGuard guard = mock(RoleGuard.class);
        AgentQueryService queries = mock(AgentQueryService.class);
        AdminAgentController admin = new AdminAgentController(guard, queries);
        admin.list();
        verify(guard).requireAnyAdmin();

        User actor = new User();
        actor.setUserId(9);
        when(guard.requireSuperAdmin()).thenReturn(actor);
        AgentAdministrationService administration = mock(AgentAdministrationService.class);
        SuperAdminAgentController superAdmin = new SuperAdminAgentController(guard,
                mock(RegistrationTokenService.class), administration);
        superAdmin.disable("agent-id");

        verify(guard).requireSuperAdmin();
        verify(administration).setEnabled("agent-id", false, 9);
    }
}
