package com.match.agent.web;

import com.match.agent.model.RegistrationTokenRequest;
import com.match.agent.service.RegistrationTokenService;
import com.match.agent.service.AgentAdministrationService;
import com.match.entity.User;
import com.match.security.RoleGuard;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SuperAdminAgentControllerTest {
    @Test
    public void tokenCreationAndListingRequireSuperAdmin() {
        RoleGuard roleGuard = mock(RoleGuard.class);
        RegistrationTokenService tokens = mock(RegistrationTokenService.class);
        User actor = new User();
        actor.setUserId(7);
        when(roleGuard.requireSuperAdmin()).thenReturn(actor);
        SuperAdminAgentController controller = new SuperAdminAgentController(roleGuard, tokens,
                mock(AgentAdministrationService.class));
        RegistrationTokenRequest request = new RegistrationTokenRequest();
        request.setLabel("机房 A");

        controller.createToken(request);
        controller.registrationTokens();

        verify(roleGuard, times(2)).requireSuperAdmin();
        verify(tokens).create(7, "机房 A");
        verify(tokens).list();
    }
}
