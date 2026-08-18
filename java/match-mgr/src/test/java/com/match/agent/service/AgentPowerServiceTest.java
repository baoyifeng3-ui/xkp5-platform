package com.match.agent.service;

import com.match.agent.model.AgentCommandView;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.entity.User;
import com.match.licensing.guard.LicenseGuard;
import com.match.licensing.guard.LicenseAccessException;
import com.match.security.UserRole;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AgentPowerServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");
    private ProcessingAgentMapper agents;
    private AgentCommandService commands;
    private LicenseGuard license;
    private WakeOnLanSender wakeSender;
    private AgentAuditService audit;
    private AgentPowerService service;
    private ProcessingAgentRecord agent;
    private User actor;

    @Before
    public void setUp() {
        agents = mock(ProcessingAgentMapper.class);
        commands = mock(AgentCommandService.class);
        license = mock(LicenseGuard.class);
        wakeSender = mock(WakeOnLanSender.class);
        audit = mock(AgentAuditService.class);
        service = new AgentPowerService(agents, commands, license, wakeSender, audit,
                Clock.fixed(NOW, ZoneOffset.UTC));
        agent = new ProcessingAgentRecord();
        agent.setAgentId("agent-id");
        agent.setMacAddress("02:23:45:67:89:ab");
        agent.setEnabled(true);
        agent.setLastSeenAt(LocalDateTime.ofInstant(NOW.minusSeconds(5), ZoneOffset.UTC));
        when(agents.selectForManagement("agent-id")).thenReturn(agent);
        actor = new User();
        actor.setUserId(7);
    }

    @Test
    public void normalAdminWakeRequiresActiveLicense() {
        service.wake("agent-id", actor, UserRole.ADMIN);

        verify(license).requireActive();
        verify(wakeSender).send(agent.getMacAddress());
        verify(audit).recordSuccess("WAKE_PACKET_SENT", 7, "agent-id", null);
    }

    @Test
    public void superAdminWakeKeepsMaintenanceAccessWithoutLicenseCheck() {
        service.wake("agent-id", actor, UserRole.SUPER_ADMIN);

        verify(license, never()).requireActive();
        verify(wakeSender).send(agent.getMacAddress());
    }

    @Test
    public void normalAdminCannotWakeWhenLicenseIsUnusable() {
        org.mockito.Mockito.doThrow(new LicenseAccessException("EXPIRED", "license expired"))
                .when(license).requireActive();

        try {
            service.wake("agent-id", actor, UserRole.ADMIN);
        } catch (LicenseAccessException expected) {
            verify(wakeSender, never()).send(agent.getMacAddress());
            verify(audit).recordFailure("WAKE_PACKET_SEND", "EXPIRED", 7, "agent-id", null);
            return;
        }
        throw new AssertionError("expected unusable license rejection");
    }

    @Test
    public void normalAdminQueuesShutdownForOnlineAgent() {
        AgentCommandView expected = new AgentCommandView();
        when(commands.requestShutdown(agent, 7, "ADMIN")).thenReturn(expected);

        AgentCommandView actual = service.shutdown("agent-id", actor, UserRole.ADMIN);

        assertSame(expected, actual);
        verify(license).requireActive();
        verify(commands).requestShutdown(agent, 7, "ADMIN");
        verify(audit).recordSuccess("SHUTDOWN_REQUESTED", 7, "agent-id", expected.getCommandId());
    }

    @Test
    public void offlineAgentCannotQueueShutdownButCanBeWoken() {
        agent.setLastSeenAt(LocalDateTime.ofInstant(NOW.minusSeconds(16), ZoneOffset.UTC));

        expectRejected(() -> service.shutdown("agent-id", actor, UserRole.ADMIN));
        service.wake("agent-id", actor, UserRole.ADMIN);

        verify(commands, never()).requestShutdown(agent, 7, "ADMIN");
        verify(wakeSender).send(agent.getMacAddress());
    }

    @Test
    public void disabledOrRemovedAgentRejectsPowerOperations() {
        agent.setEnabled(false);
        expectRejected(() -> service.wake("agent-id", actor, UserRole.SUPER_ADMIN));
        agent.setEnabled(true);
        agent.setRemovedAt(LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
        expectRejected(() -> service.shutdown("agent-id", actor, UserRole.SUPER_ADMIN));
        verify(wakeSender, never()).send(agent.getMacAddress());
    }

    @Test
    public void nonAdminRoleIsRejectedDefensively() {
        expectRejected(() -> service.wake("agent-id", actor, UserRole.USER));
        verify(wakeSender, never()).send(agent.getMacAddress());
    }

    @Test
    public void missingActorIsRejectedBeforeSendingPacket() {
        expectRejected(() -> service.wake("agent-id", null, UserRole.SUPER_ADMIN));
        verify(wakeSender, never()).send(agent.getMacAddress());
    }

    private void expectRejected(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected power operation rejection");
    }
}
