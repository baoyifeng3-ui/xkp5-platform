package com.match.controller;

import com.match.entity.TrainingServer;
import com.match.security.RoleGuard;
import com.match.service.impl.TrainingEnvironmentService;
import org.junit.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

public class AdminTrainingControllerTest {
    @Test
    public void statusIsVisibleToAnyAdministrator() {
        TrainingEnvironmentService service = mock(TrainingEnvironmentService.class);
        RoleGuard roleGuard = mock(RoleGuard.class);
        AdminTrainingController controller = new AdminTrainingController(service, roleGuard);

        controller.servers();

        InOrder order = inOrder(roleGuard, service);
        order.verify(roleGuard).requireAnyAdmin();
        order.verify(service).listServers();
    }

    @Test
    public void serverMutationRequiresSuperAdministrator() {
        TrainingEnvironmentService service = mock(TrainingEnvironmentService.class);
        RoleGuard roleGuard = mock(RoleGuard.class);
        AdminTrainingController controller = new AdminTrainingController(service, roleGuard);
        TrainingServer server = new TrainingServer();

        controller.createServer(server);

        InOrder order = inOrder(roleGuard, service);
        order.verify(roleGuard).requireSuperAdmin();
        order.verify(service).saveServer(server);
    }
}
