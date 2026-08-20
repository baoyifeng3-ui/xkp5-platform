package com.match.agent.web;

import com.match.agent.service.AgentAdministrationService;
import com.match.agent.service.AgentQueryService;
import com.match.agent.service.AgentPowerService;
import com.match.agent.service.AgentCommandService;
import com.match.agent.service.RegistrationTokenService;
import com.match.entity.User;
import com.match.security.RoleGuard;
import com.match.security.UserRole;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

public class AgentRoleBoundaryTest {
    @Test
    public void normalAdminCanReadButSuperAdminOwnsMutations() {
        RoleGuard guard = mock(RoleGuard.class);
        AgentQueryService queries = mock(AgentQueryService.class);
        AgentPowerService power = mock(AgentPowerService.class);
        User normalAdmin = new User();
        normalAdmin.setUserId(8);
        when(guard.requireAnyAdmin()).thenReturn(normalAdmin);
        when(guard.roleOf(normalAdmin)).thenReturn(UserRole.ADMIN);
        AgentCommandService commands = mock(AgentCommandService.class);
        AdminAgentController admin = new AdminAgentController(guard, queries, power, commands);
        admin.list();
        admin.wake("agent-id");
        admin.commands("agent-id");
        verify(guard, times(3)).requireAnyAdmin();
        verify(power).wake("agent-id", normalAdmin, UserRole.ADMIN);
        verify(commands).recent("agent-id");

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
