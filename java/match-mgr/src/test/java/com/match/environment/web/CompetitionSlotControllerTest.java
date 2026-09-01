package com.match.environment.web;

import com.match.entity.User;
import com.match.environment.model.BindCompetitionSlotRequest;
import com.match.environment.service.CompetitionSlotBindingService;
import com.match.environment.service.EnvironmentOperationService;
import com.match.environment.persistence.EnvironmentPortAllocationMapper;
import com.match.environment.persistence.EnvironmentPortAllocationRecord;
import com.match.environment.persistence.TrainingEnvironmentRecord;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.security.AdminAccessException;
import com.match.security.LoginSession;
import com.match.security.RoleGuard;
import com.match.service.impl.UserServiceImpl;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Collections;
import java.util.Map;

public class CompetitionSlotControllerTest {
    private static final String SLOT_ID = "22222222-2222-4222-8222-222222222222";

    @Test
    public void adminControllerUsesExactBusinessAdminBoundary() {
        assertAdminBoundary("ADMIN", true);
        assertAdminDenied("SUPER_ADMIN", true);
        assertAdminDenied("USER", true);
        assertAdminDenied("ADMIN", false);
    }

    @Test
    public void bindingAndUnbindingUseAuthenticatedAdminAndRequestedTargetUser() {
        Fixture fixture = fixture("ADMIN", true, 7);
        BindCompetitionSlotRequest request = new BindCompetitionSlotRequest();
        request.setUserId(21);

        fixture.adminController.bind(SLOT_ID, request);
        fixture.adminController.unbind(SLOT_ID, 21);

        verify(fixture.service).bind(SLOT_ID, 21, fixture.current);
        verify(fixture.service).unbind(SLOT_ID, 21, fixture.current);
    }

    @Test
    public void participantControllerNeverTrustsCallerSuppliedUserId() {
        Fixture fixture = fixture("USER", true, 21);

        assertEquals(200, fixture.userController.current().getCode());

        verify(fixture.service).currentForUser(fixture.current);
    }

    @Test
    public void administratorsCannotReadParticipantCompetitionEndpoint() {
        Fixture fixture = fixture("ADMIN", true, 7);
        try {
            fixture.userController.current();
            fail("ADMIN must not use USER endpoint");
        } catch (AdminAccessException expected) {
            assertEquals("仅普通用户可以执行此操作", expected.getMessage());
        }
    }

    @Test
    public void participantCompetitionEndpointUsesCurrentCompetitionTrainingEnvironment() {
        RoleGuard guard = mock(RoleGuard.class);
        CompetitionSlotBindingService legacy = mock(CompetitionSlotBindingService.class);
        EnvironmentOperationService operations = mock(EnvironmentOperationService.class);
        EnvironmentPortAllocationMapper ports = mock(EnvironmentPortAllocationMapper.class);
        ProcessingAgentMapper agents = mock(ProcessingAgentMapper.class);
        User participant = new User();
        participant.setUserId(21);
        TrainingEnvironmentRecord environment = new TrainingEnvironmentRecord();
        environment.setEnvironmentId("environment-1");
        environment.setEnvironmentType("COMPETITION");
        environment.setAgentId("agent-1");
        environment.setActualState("RUNNING");
        ProcessingAgentRecord agent = new ProcessingAgentRecord();
        agent.setPrimaryIp("10.0.0.8");
        EnvironmentPortAllocationRecord t100 = new EnvironmentPortAllocationRecord();
        t100.setComponentType("EDITOR");
        t100.setContainerPort(5000);
        t100.setHostPort(5501);
        when(guard.requireUser()).thenReturn(participant);
        when(operations.listForUser(21)).thenReturn(Collections.singletonList(environment));
        when(agents.selectForManagement("agent-1")).thenReturn(agent);
        when(ports.selectByEnvironment("environment-1")).thenReturn(Collections.singletonList(t100));

        UserCompetitionEnvironmentController controller = new UserCompetitionEnvironmentController(
                guard, legacy, operations, ports, agents);
        Map<?, ?> view = (Map<?, ?>) controller.current().getData();

        assertEquals("environment-1", view.get("environmentId"));
        assertEquals("RUNNING", view.get("readiness"));
        assertEquals("10.0.0.8:5501", view.get("t100Url"));
    }

    private void assertAdminBoundary(String role, boolean enabled) {
        Fixture fixture = fixture(role, enabled, 7);
        assertEquals(200, fixture.adminController.list().getCode());
        verify(fixture.service).list(fixture.current);
    }

    private void assertAdminDenied(String role, boolean enabled) {
        Fixture fixture = fixture(role, enabled, 7);
        try {
            fixture.adminController.list();
            fail(role + " must not use ADMIN slot endpoint");
        } catch (AdminAccessException expected) {
            assertEquals("仅普通管理员可以执行此操作", expected.getMessage());
        }
    }

    private Fixture fixture(String role, boolean enabled, int id) {
        User current = new User();
        current.setUserId(id);
        current.setUserName(role.toLowerCase());
        current.setRole(role);
        current.setEnabled(enabled);
        current.setMustChangePassword(false);
        UserServiceImpl users = mock(UserServiceImpl.class);
        LoginSession session = mock(LoginSession.class);
        when(session.loginId()).thenReturn(id);
        when(users.getById(id)).thenReturn(current);
        RoleGuard guard = new RoleGuard(users, session);
        CompetitionSlotBindingService service = mock(CompetitionSlotBindingService.class);
        return new Fixture(current, service,
                new AdminCompetitionSlotController(guard, service),
                new UserCompetitionEnvironmentController(guard, service));
    }

    private static final class Fixture {
        private final User current;
        private final CompetitionSlotBindingService service;
        private final AdminCompetitionSlotController adminController;
        private final UserCompetitionEnvironmentController userController;

        private Fixture(User current, CompetitionSlotBindingService service,
                        AdminCompetitionSlotController adminController,
                        UserCompetitionEnvironmentController userController) {
            this.current = current;
            this.service = service;
            this.adminController = adminController;
            this.userController = userController;
        }
    }
}
