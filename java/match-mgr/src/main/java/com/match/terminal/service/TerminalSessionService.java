package com.match.terminal.service;

import com.match.agent.model.AgentCommandView;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentCommandService;
import com.match.agent.service.AgentAuditService;
import com.match.agent.web.AgentProtocolException;
import com.match.entity.User;
import com.match.security.UserRole;
import com.match.terminal.model.TerminalSessionView;
import com.match.terminal.model.TerminalTicketView;
import com.match.terminal.model.TerminalPollingInputRequest;
import com.match.terminal.model.TerminalPollingOutputView;
import com.match.terminal.persistence.TerminalSessionMapper;
import com.match.terminal.persistence.TerminalSessionRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

@Service
public class TerminalSessionService {
    private static final String CONFIRMATION = "OPEN_ROOT_TERMINAL";
    private static final Duration ONLINE_WINDOW = Duration.ofSeconds(15);
    private static final Duration AGENT_CONNECTION_WINDOW = Duration.ofSeconds(90);
    private static final Duration ABSOLUTE_LIFETIME = Duration.ofHours(2);
    private static final Duration TICKET_LIFETIME = Duration.ofSeconds(60);
    private static final Duration BROWSER_CONNECTION_WINDOW = Duration.ofSeconds(60);

    private final TerminalSessionMapper sessionMapper;
    private final ProcessingAgentMapper agentMapper;
    private final AgentCommandService commandService;
    private final Clock clock;
    private final SecureRandom secureRandom;
    private final TerminalRelayLifecycle relayLifecycle;
    private final AtomicBoolean startupRecoveryComplete;
    private final AgentAuditService auditService;

    @Autowired
    public TerminalSessionService(TerminalSessionMapper sessionMapper, ProcessingAgentMapper agentMapper,
                                  AgentCommandService commandService, Clock clock,
                                  ObjectProvider<TerminalRelayLifecycle> relayLifecycles,
                                  AgentAuditService auditService) {
        this(sessionMapper, agentMapper, commandService, clock, new SecureRandom(),
                providerLifecycle(relayLifecycles), auditService);
        beginStartupRecovery();
    }

    public TerminalSessionService(TerminalSessionMapper sessionMapper, ProcessingAgentMapper agentMapper,
                                  AgentCommandService commandService, Clock clock,
                                  ObjectProvider<TerminalRelayLifecycle> relayLifecycles) {
        this(sessionMapper, agentMapper, commandService, clock, new SecureRandom(),
                providerLifecycle(relayLifecycles), null);
        beginStartupRecovery();
    }

    private static TerminalRelayLifecycle providerLifecycle(
            ObjectProvider<TerminalRelayLifecycle> relayLifecycles) {
        return new TerminalRelayLifecycle() {
            @Override
            public void closePersistedSession(String sessionId, Runnable persistenceClose) {
                TerminalRelayLifecycle lifecycle = relayLifecycles.getIfAvailable();
                if (lifecycle == null) {
                    persistenceClose.run();
                } else {
                    lifecycle.closePersistedSession(sessionId, persistenceClose);
                }
            }

            @Override
            public boolean closePersistedSessionIf(String sessionId,
                                                   BooleanSupplier persistenceClose) {
                TerminalRelayLifecycle lifecycle = relayLifecycles.getIfAvailable();
                return lifecycle == null ? persistenceClose.getAsBoolean()
                        : lifecycle.closePersistedSessionIf(sessionId, persistenceClose);
            }

            @Override
            public ConditionalCloseResult closePersistedSessionConditionally(String sessionId,
                    Supplier<ConditionalCloseDecision> persistenceClose) {
                TerminalRelayLifecycle lifecycle = relayLifecycles.getIfAvailable();
                return lifecycle == null
                        ? TerminalRelayLifecycle.super.closePersistedSessionConditionally(
                                sessionId, persistenceClose)
                        : lifecycle.closePersistedSessionConditionally(sessionId, persistenceClose);
            }
        };
    }

    public TerminalSessionService(TerminalSessionMapper sessionMapper, ProcessingAgentMapper agentMapper,
                                  AgentCommandService commandService, Clock clock) {
        this(sessionMapper, agentMapper, commandService, clock, new SecureRandom(),
                TerminalSessionService::runPersistenceClose, null);
    }

    public TerminalSessionService(TerminalSessionMapper sessionMapper, ProcessingAgentMapper agentMapper,
                                  AgentCommandService commandService, Clock clock,
                                  SecureRandom secureRandom) {
        this(sessionMapper, agentMapper, commandService, clock, secureRandom,
                TerminalSessionService::runPersistenceClose, null);
    }

    public TerminalSessionService(TerminalSessionMapper sessionMapper, ProcessingAgentMapper agentMapper,
                                  AgentCommandService commandService, Clock clock,
                                  SecureRandom secureRandom, TerminalRelayLifecycle relayLifecycle) {
        this(sessionMapper, agentMapper, commandService, clock, secureRandom, relayLifecycle, null);
    }

    public TerminalSessionService(TerminalSessionMapper sessionMapper, ProcessingAgentMapper agentMapper,
                                  AgentCommandService commandService, Clock clock,
                                  SecureRandom secureRandom, TerminalRelayLifecycle relayLifecycle,
                                  AgentAuditService auditService) {
        this.sessionMapper = sessionMapper;
        this.agentMapper = agentMapper;
        this.commandService = commandService;
        this.clock = clock;
        this.secureRandom = secureRandom;
        this.relayLifecycle = relayLifecycle;
        this.startupRecoveryComplete = new AtomicBoolean(true);
        this.auditService = auditService;
    }

    public void beginStartupRecovery() {
        startupRecoveryComplete.set(false);
    }

    public void completeStartupRecovery() {
        startupRecoveryComplete.set(true);
    }

    public boolean isStartupRecoveryComplete() {
        return startupRecoveryComplete.get();
    }

    public TerminalSessionView view(String sessionId, User actor) {
        requireSuperAdmin(actor);
        TerminalSessionRecord record = requireSession(sessionId);
        return toView(record);
    }

    @Transactional(readOnly = true)
    public TerminalPollingOutputView pollOutput(String sessionId, long cursor) {
        TerminalSessionRecord record = requireSession(sessionId);
        if (cursor < 0) throw new IllegalArgumentException("输出游标不能为负数");
        TerminalPollingOutputView view = new TerminalPollingOutputView();
        view.setCursor(cursor);
        view.setNextCursor(cursor);
        view.setReset(false);
        view.setState(record.getState());
        view.setChunks(Collections.emptyList());
        return view;
    }

    @Transactional
    public void queueInput(String sessionId, User actor, TerminalPollingInputRequest request) {
        requireSuperAdmin(actor);
        requireSession(sessionId);
        String data = request == null ? null : request.getData();
        if (data == null || data.isEmpty() || data.getBytes(StandardCharsets.UTF_8).length > 4096) {
            throw new IllegalArgumentException("终端输入不能为空且不能超过 4 KiB");
        }
        throw new TerminalSessionException("TERMINAL_POLLING_NOT_READY", "终端轮询输入通道正在接入", HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Transactional
    public TerminalTicketView issueAgentTicket(ProcessingAgentRecord agent, String sessionId,
                                                String commandId, String leaseToken) {
        if (!startupRecoveryComplete.get()) {
            throw ticketUnavailable();
        }
        TerminalSessionRecord record = sessionMapper.selectById(sessionId);
        if (record == null || agent == null || !agent.getAgentId().equals(record.getAgentId())
                || !agent.getAgentId().equals(record.getActiveAgentId())
                || !"WAITING_AGENT".equals(record.getState())
                || !commandIdEquals(record, commandId)
                || !commandService.hasRunningTerminalLease(commandId, agent.getAgentId(), leaseToken, sessionId)) {
            throw ticketUnavailable();
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = earliest(now.plus(TICKET_LIFETIME),
                toInstant(record.getRequestedAt()).plus(AGENT_CONNECTION_WINDOW),
                toInstant(record.getAbsoluteExpiresAt()));
        if (!expiresAt.isAfter(now)) {
            throw ticketUnavailable();
        }
        TerminalTicketView ticket = persistTicket(sessionId, expiresAt, now, true);
        audit(record, "AGENT_TICKET_ISSUED", "SUCCESS", null);
        return ticket;
    }

    @Transactional
    public boolean consumeAgentTicket(ProcessingAgentRecord agent, String sessionId, String ticket) {
        if (!startupRecoveryComplete.get()) {
            return false;
        }
        Instant now = clock.instant();
        if (agent == null || ticket == null || !Boolean.TRUE.equals(agent.getEnabled())
                || agent.getRemovedAt() != null || !isOnline(agent, now)) {
            return false;
        }
        TerminalSessionRecord record = sessionMapper.selectById(sessionId);
        if (record == null || !agent.getAgentId().equals(record.getAgentId())
                || !agent.getAgentId().equals(record.getActiveAgentId())) {
            return false;
        }
        boolean consumed = sessionMapper.consumeAgentTicket(sessionId, agent.getAgentId(),
                digest(ticket), utc(now)) == 1;
        if (consumed) {
            audit(record, "AGENT_ATTACHED", "SUCCESS", null);
        }
        return consumed;
    }

    public boolean isRelayAttachmentEligible(String sessionId, String role, String agentId) {
        if (!startupRecoveryComplete.get() || sessionId == null || role == null) {
            return false;
        }
        TerminalSessionRecord record = sessionMapper.selectById(sessionId);
        Instant now = clock.instant();
        if (record == null || !"WAITING_BROWSER".equals(record.getState())
                || record.getAbsoluteExpiresAt() == null
                || !toInstant(record.getAbsoluteExpiresAt()).isAfter(now)
                || record.getAgentConnectedAt() == null
                || !toInstant(record.getAgentConnectedAt()).plus(BROWSER_CONNECTION_WINDOW)
                .isAfter(now)) {
            return false;
        }
        if ("BROWSER".equals(role)) {
            return agentId == null && record.getBrowserTicketConsumedAt() != null
                    && record.getBrowserConnectedAt() != null;
        }
        return "AGENT".equals(role) && agentId != null
                && agentId.equals(record.getAgentId())
                && agentId.equals(record.getActiveAgentId())
                && record.getAgentTicketConsumedAt() != null
                && record.getAgentConnectedAt() != null;
    }

    @Transactional
    public TerminalTicketView issueBrowserTicket(String sessionId, User actor) {
        requireSuperAdmin(actor);
        if (!startupRecoveryComplete.get()) {
            throw ticketUnavailable();
        }
        TerminalSessionRecord record = requireSession(sessionId);
        if (!"WAITING_BROWSER".equals(record.getState()) || record.getAgentConnectedAt() == null
                || record.getBrowserTicketConsumedAt() != null
                || record.getBrowserConnectedAt() != null) {
            throw ticketUnavailable();
        }
        Instant now = clock.instant();
        Instant expiresAt = earliest(now.plus(TICKET_LIFETIME),
                toInstant(record.getAgentConnectedAt()).plus(BROWSER_CONNECTION_WINDOW),
                toInstant(record.getAbsoluteExpiresAt()));
        if (!expiresAt.isAfter(now)) {
            throw ticketUnavailable();
        }
        TerminalTicketView ticket = persistTicket(sessionId, expiresAt, now, false);
        audit(record, "BROWSER_TICKET_ISSUED", "SUCCESS", null);
        return ticket;
    }

    @Transactional
    public boolean consumeBrowserTicket(String sessionId, String ticket) {
        if (!startupRecoveryComplete.get() || sessionId == null || ticket == null) {
            return false;
        }
        Instant now = clock.instant();
        TerminalSessionRecord record = sessionMapper.selectById(sessionId);
        if (record == null || !sessionId.equals(record.getSessionId())
                || !"WAITING_BROWSER".equals(record.getState())
                || record.getAgentConnectedAt() == null
                || record.getBrowserTicketExpiresAt() == null
                || record.getAbsoluteExpiresAt() == null
                || !toInstant(record.getBrowserTicketExpiresAt()).isAfter(now)
                || !toInstant(record.getAbsoluteExpiresAt()).isAfter(now)
                || !toInstant(record.getAgentConnectedAt()).plus(BROWSER_CONNECTION_WINDOW)
                .isAfter(now)) {
            return false;
        }
        boolean consumed = sessionMapper.consumeBrowserTicket(sessionId, digest(ticket), utc(now)) == 1;
        if (consumed) {
            audit(record, "BROWSER_ATTACHED", "SUCCESS", null);
        }
        return consumed;
    }

    @Transactional
    public boolean markRelayActive(String sessionId) {
        boolean active = sessionId != null
                && sessionMapper.markActive(sessionId, utc(clock.instant())) == 1;
        if (active) {
            audit(sessionMapper.selectById(sessionId), "TERMINAL_ACTIVE", "SUCCESS", null);
        }
        return active;
    }

    public boolean recordRelayTraffic(String sessionId, long browserToAgentTotal,
                                      long agentToBrowserTotal) {
        if (sessionId == null || browserToAgentTotal < 0 || agentToBrowserTotal < 0
                || (browserToAgentTotal == 0 && agentToBrowserTotal == 0)) {
            return false;
        }
        return sessionMapper.setTrafficTotals(sessionId, browserToAgentTotal, agentToBrowserTotal,
                utc(clock.instant())) == 1;
    }

    public boolean isRelayStillOpen(String sessionId) {
        TerminalSessionRecord record = sessionId == null ? null : sessionMapper.selectById(sessionId);
        return record != null && !isTerminal(record.getState());
    }

    public Set<String> findOpenRelaySessionIds(List<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Collections.emptySet();
        }
        if (sessionIds.size() > 200) {
            throw new IllegalArgumentException("Terminal relay state lookup exceeds batch limit");
        }
        Set<String> requested = new HashSet<>(sessionIds);
        Set<String> open = new HashSet<>();
        for (TerminalSessionRecord record : sessionMapper.selectStatesByIds(sessionIds)) {
            if (record != null && requested.contains(record.getSessionId())
                    && !isTerminal(record.getState())) {
                open.add(record.getSessionId());
            }
        }
        return open;
    }

    @Transactional
    public void finishRelay(String sessionId, boolean operatorClosed, String reason) {
        String state = operatorClosed ? "CLOSED" : "FAILED";
        String stableReason = operatorClosed ? "OPERATOR_CLOSED" : stableRelayFailure(reason);
        String message = operatorClosed ? "Terminal session closed by operator"
                : "Terminal relay closed";
        if (sessionMapper.close(sessionId, state, stableReason, message, utc(clock.instant())) == 1) {
            audit(sessionMapper.selectById(sessionId), "TERMINAL_CLOSE",
                    operatorClosed ? "SUCCESS" : "FAILURE", stableReason);
        }
    }

    private String stableRelayFailure(String reason) {
        if ("PEER_DISCONNECTED".equals(reason) || "PROTOCOL_ERROR".equals(reason)
                || "BACKPRESSURE".equals(reason) || "RATE_LIMITED".equals(reason)
                || "SESSION_REJECTED".equals(reason) || "IO_ERROR".equals(reason)) {
            return reason;
        }
        return "RELAY_FAILURE";
    }

    @Transactional
    public void close(String sessionId, User actor) {
        requireSuperAdmin(actor);
        TerminalSessionRecord record = requireSession(sessionId);
        if (isTerminal(record.getState())) {
            commandService.cancelTerminalCommand(record.getCommandId());
            relayLifecycle.closePersistedSessionConditionally(sessionId,
                    () -> TerminalRelayLifecycle.ConditionalCloseDecision.LOCAL_ONLY);
            return;
        }
        relayLifecycle.closePersistedSessionConditionally(sessionId, () -> {
            Instant now = clock.instant();
            if (sessionMapper.close(sessionId, "CLOSED", "OPERATOR_CLOSED",
                    "Terminal session closed by operator", utc(now)) != 1) {
                TerminalSessionRecord current = sessionMapper.selectById(sessionId);
                if (current == null || !isTerminal(current.getState())) {
                    throw error("TERMINAL_SESSION_CLOSE_FAILED", "Terminal session could not be closed");
                }
                return TerminalRelayLifecycle.ConditionalCloseDecision.LOCAL_ONLY;
            }
            audit(record, "TERMINAL_CLOSE", "SUCCESS", "OPERATOR_CLOSED");
            commandService.cancelTerminalCommand(record.getCommandId());
            return TerminalRelayLifecycle.ConditionalCloseDecision.PERSISTED;
        });
    }

    @Transactional
    public TerminalSessionView create(String agentId, User actor, String confirmation) {
        requireSuperAdmin(actor);
        if (!startupRecoveryComplete.get()) {
            throw new TerminalSessionException("TERMINAL_STARTUP_RECOVERY",
                    "Terminal management startup recovery is in progress",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (!CONFIRMATION.equals(confirmation)) {
            throw error("TERMINAL_CONFIRMATION_REQUIRED",
                    "Exact root terminal confirmation is required");
        }

        ProcessingAgentRecord agent = agentMapper.selectForManagement(agentId);
        if (agent == null || !Boolean.TRUE.equals(agent.getEnabled()) || agent.getRemovedAt() != null) {
            throw error("TERMINAL_AGENT_UNAVAILABLE", "Processing Agent is unavailable");
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        if (!isOnline(agent, now)) {
            throw error("TERMINAL_AGENT_OFFLINE", "Processing Agent is offline");
        }

        Instant agentConnectionDeadline = now.plus(AGENT_CONNECTION_WINDOW);
        Instant absoluteExpiresAt = now.plus(ABSOLUTE_LIFETIME);
        LocalDateTime utcNow = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        TerminalSessionRecord record = new TerminalSessionRecord();
        record.setSessionId(UUID.randomUUID().toString());
        record.setAgentId(agent.getAgentId());
        record.setRequesterUserId(actor.getUserId());
        record.setRequesterRole(UserRole.SUPER_ADMIN.name());
        record.setState("WAITING_AGENT");
        record.setActiveAgentId(agent.getAgentId());
        record.setRequestedAt(utcNow);
        record.setAbsoluteExpiresAt(LocalDateTime.ofInstant(absoluteExpiresAt, ZoneOffset.UTC));
        record.setUpdatedAt(utcNow);
        try {
            sessionMapper.insert(record);
        } catch (DuplicateKeyException collision) {
            TerminalSessionRecord active = sessionMapper.selectActiveByAgent(agent.getAgentId());
            if (active != null && active.getSessionId() != null) {
                return toView(active);
            }
            throw error("TERMINAL_SESSION_ACTIVE",
                    "An active terminal session already exists for this Agent");
        }

        if (sessionMapper.markPollingActive(record.getSessionId(), utcNow) != 1) {
            throw error("TERMINAL_SESSION_START_FAILED", "轮询终端会话无法启动");
        }
        record.setState("ACTIVE");
        if (auditService != null) {
            auditService.recordTerminal("TERMINAL_REQUEST", "SUCCESS", null,
                    record.getRequesterUserId(), record.getAgentId(), record.getSessionId(),
                    null);
        }

        TerminalSessionView view = new TerminalSessionView();
        view.setSessionId(record.getSessionId());
        view.setAgentId(record.getAgentId());
        view.setState("ACTIVE");
        view.setRequestedAt(now);
        view.setAgentConnectionDeadline(agentConnectionDeadline);
        view.setAbsoluteExpiresAt(absoluteExpiresAt);
        view.setCommandId(null);
        return view;
    }

    private boolean isSuperAdmin(User actor) {
        if (actor == null || actor.getUserId() == null) {
            return false;
        }
        try {
            return UserRole.resolve(actor.getRole(), actor.getIsAdmin(), actor.getUserName())
                    == UserRole.SUPER_ADMIN;
        } catch (RuntimeException invalidRole) {
            return false;
        }
    }

    private void requireSuperAdmin(User actor) {
        if (!isSuperAdmin(actor)) {
            throw new TerminalSessionException("TERMINAL_SUPER_ADMIN_REQUIRED",
                    "Only a super administrator can access a root terminal", HttpStatus.FORBIDDEN);
        }
    }

    private TerminalSessionRecord requireSession(String sessionId) {
        TerminalSessionRecord record = sessionMapper.selectById(sessionId);
        if (record == null) {
            throw new TerminalSessionException("TERMINAL_SESSION_NOT_FOUND",
                    "Terminal session was not found", HttpStatus.NOT_FOUND);
        }
        return record;
    }

    private TerminalTicketView persistTicket(String sessionId, Instant expiresAt,
                                             Instant now, boolean agentTicket) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        int updated = agentTicket
                ? sessionMapper.issueAgentTicket(sessionId, digest(ticket), utc(expiresAt), utc(now))
                : sessionMapper.issueBrowserTicket(sessionId, digest(ticket), utc(expiresAt), utc(now));
        if (updated != 1) {
            throw ticketUnavailable();
        }
        TerminalTicketView view = new TerminalTicketView();
        view.setTicket(ticket);
        view.setExpiresAt(expiresAt);
        return view;
    }

    private TerminalSessionView toView(TerminalSessionRecord record) {
        TerminalSessionView view = new TerminalSessionView();
        view.setSessionId(record.getSessionId());
        view.setAgentId(record.getAgentId());
        view.setState(record.getState());
        view.setRequestedAt(toInstant(record.getRequestedAt()));
        view.setAgentConnectionDeadline(toInstant(record.getRequestedAt()).plus(AGENT_CONNECTION_WINDOW));
        view.setAbsoluteExpiresAt(toInstant(record.getAbsoluteExpiresAt()));
        view.setCommandId(record.getCommandId());
        return view;
    }

    private void audit(TerminalSessionRecord record, String action, String result, String reason) {
        if (auditService == null || record == null) {
            return;
        }
        auditService.recordTerminal(action, result, reason, record.getRequesterUserId(),
                record.getAgentId(), record.getSessionId(), record.getCommandId());
    }

    private String digest(String ticket) {
        try {
            byte[] value = MessageDigest.getInstance("SHA-256")
                    .digest(ticket.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(value.length * 2);
            for (byte item : value) {
                hex.append(String.format("%02x", item & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private Instant earliest(Instant first, Instant second, Instant third) {
        Instant value = first.isBefore(second) ? first : second;
        return value.isBefore(third) ? value : third;
    }

    private Instant toInstant(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC);
    }

    private LocalDateTime utc(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private boolean commandIdEquals(TerminalSessionRecord record, String commandId) {
        return commandId != null && commandId.equals(record.getCommandId());
    }

    private boolean isTerminal(String state) {
        return "CLOSED".equals(state) || "FAILED".equals(state);
    }

    private TerminalSessionException ticketUnavailable() {
        return new TerminalSessionException("TERMINAL_TICKET_UNAVAILABLE",
                "Terminal ticket is unavailable", HttpStatus.CONFLICT);
    }

    private boolean isOnline(ProcessingAgentRecord agent, Instant now) {
        if (agent.getLastSeenAt() == null) {
            return false;
        }
        Instant lastSeen = agent.getLastSeenAt().toInstant(ZoneOffset.UTC);
        return !lastSeen.isBefore(now.minus(ONLINE_WINDOW)) && !lastSeen.isAfter(now);
    }

    private TerminalSessionException error(String code, String message) {
        return new TerminalSessionException(code, message);
    }

    private static void runPersistenceClose(String sessionId, Runnable persistenceClose) {
        persistenceClose.run();
    }
}
