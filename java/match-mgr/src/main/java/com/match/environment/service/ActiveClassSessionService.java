package com.match.environment.service;

import com.match.environment.persistence.ActiveClassSessionMapper;
import com.match.environment.persistence.ActiveClassSessionRecord;
import com.match.environment.persistence.TrainingEnvironmentMapper;
import com.match.environment.persistence.TrainingEnvironmentRecord;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.mapper.UserMapper;
import com.match.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ActiveClassSessionService {
    private final ActiveClassSessionMapper sessions;
    private final TrainingEnvironmentMapper environments;
    private final EnvironmentOperationService operationService;
    private final ProcessingAgentMapper agents;
    private final UserMapper users;
    @org.springframework.beans.factory.annotation.Autowired private com.match.mode.persistence.ProcessingAgentModeMapper agentModes;
    @org.springframework.beans.factory.annotation.Autowired private com.match.mode.persistence.PlatformModeMapper platformModes;
    @org.springframework.beans.factory.annotation.Autowired private com.match.environment.persistence.EnvironmentOperationMapper operationRecords;
    public ActiveClassSessionService(ActiveClassSessionMapper sessions, TrainingEnvironmentMapper environments,
                                     EnvironmentOperationService operationService,
                                     ProcessingAgentMapper agents, UserMapper users) {
        this.sessions = sessions; this.environments = environments; this.operationService = operationService;
        this.agents = agents; this.users = users;
    }

    @Transactional public ActiveClassSessionRecord startCourse(String courseId, String editorTool,
                                                                boolean ignoreOffline, Integer actorId, String actorRole) {
        if (courseId == null || courseId.trim().isEmpty()) throw new IllegalArgumentException("请选择课程");
        operationService.requireTrainingMode();
        requireModeReady();
        java.util.List<TrainingEnvironmentRecord> rows = environments.selectByCourse(courseId);
        if (rows == null || rows.isEmpty()) throw new IllegalArgumentException("该课程尚未创建实训环境");
        requireOnlineOrIgnored(rows, ignoreOffline);
        ActiveClassSessionRecord session = currentForUpdate();
        if (Boolean.TRUE.equals(session.getActive()) && java.util.Objects.equals(courseId, session.getCourseId())
                && java.util.Objects.equals(normalizeTool(editorTool), session.getEditorTool())) return session;
        session.setEnvironmentId(null); session.setEnvironmentName(null); session.setCourseId(courseId); session.setEditorTool(normalizeTool(editorTool)); session.setActive(true);
        session.setStartedBy(actorId); session.setStartedAt(LocalDateTime.now()); session.setUpdatedAt(LocalDateTime.now());
        sessions.activate(null, null, courseId, session.getEditorTool(), actorId, session.getUpdatedAt());
        for (TrainingEnvironmentRecord row : rows) operationService.start(row.getEnvironmentId(), actorId, actorRole);
        return session;
    }

    @Transactional public ActiveClassSessionRecord startIndependent(String environmentName, String editorTool,
                                                                     boolean ignoreOffline, Integer actorId, String actorRole) {
        if (environmentName == null || environmentName.trim().isEmpty()) throw new IllegalArgumentException("请选择独立实训环境");
        operationService.requireTrainingMode();
        requireModeReady();
        java.util.List<TrainingEnvironmentRecord> rows = environments.selectIndependentByName(environmentName);
        if (rows == null || rows.isEmpty()) throw new IllegalArgumentException("独立实训环境不存在");
        requireOnlineOrIgnored(rows, ignoreOffline);
        ActiveClassSessionRecord session = currentForUpdate();
        if (Boolean.TRUE.equals(session.getActive()) && session.getCourseId() == null
                && java.util.Objects.equals(environmentName, session.getEnvironmentName())
                && java.util.Objects.equals(normalizeTool(editorTool), session.getEditorTool())) return session;
        session.setEnvironmentId(null); session.setEnvironmentName(environmentName); session.setCourseId(null); session.setEditorTool(normalizeTool(editorTool)); session.setActive(true);
        session.setStartedBy(actorId); session.setStartedAt(LocalDateTime.now()); session.setUpdatedAt(LocalDateTime.now());
        sessions.activate(null, environmentName, null, session.getEditorTool(), actorId, session.getUpdatedAt());
        for (TrainingEnvironmentRecord row : rows) operationService.start(row.getEnvironmentId(), actorId, actorRole);
        return session;
    }

    private String normalizeTool(String tool) { return "JUPYTER".equals(tool) ? "JUPYTER" : "VSCODE"; }
    public ActiveClassSessionRecord current() { return sessions.selectCurrent(); }

    public Map<String, Object> userPolicy() {
        ActiveClassSessionRecord session = sessions.selectCurrent();
        Map<String, Object> result = new LinkedHashMap<>();
        boolean active = session != null && Boolean.TRUE.equals(session.getActive());
        result.put("active", active);
        result.put("courseId", active ? session.getCourseId() : null);
        result.put("environmentName", active ? session.getEnvironmentName() : null);
        result.put("editorTool", active ? session.getEditorTool() : null);
        result.put("revision", session == null || session.getUpdatedAt() == null ? "" : session.getUpdatedAt().toString());
        result.put("allowedRoutes", active
                ? java.util.Arrays.asList("/training-environment", "/training-validation")
                : java.util.Collections.emptyList());
        return result;
    }

    public Map<String, Object> userPolicy(int userId) {
        Map<String, Object> result = userPolicy();
        List<TrainingEnvironmentRecord> rows = environments.selectByUser(userId);
        result.put("switching", rows.stream().anyMatch(row -> preparing(row.getActualState())));
        result.put("modeSwitching", agentModes != null && rows.stream().map(TrainingEnvironmentRecord::getAgentId).distinct()
                .anyMatch(id -> { com.match.mode.persistence.ProcessingAgentModeRecord mode = agentModes.selectById(id); return mode != null && mode.getActiveTransitionId() != null; }));
        result.put("mode", platformModes == null ? "TRAINING" : platformModes.selectCurrent().getMode());
        result.put("stopping", !Boolean.TRUE.equals(result.get("active")) && stoppingClass(rows));
        return result;
    }

    private boolean preparing(String state) { return java.util.Arrays.asList("CREATING", "STARTING", "STOPPING", "RESTORING", "WAITING_DEPENDENCY").contains(state); }

    private boolean stoppingClass(List<TrainingEnvironmentRecord> rows) {
        if (operationRecords == null) return false;
        return rows.stream().filter(row -> "STOPPING".equals(row.getActualState())).anyMatch(row -> {
            List<com.match.environment.persistence.EnvironmentOperationRecord> ops = operationRecords.selectRecent(row.getEnvironmentId(), 1);
            return !ops.isEmpty() && !"USER".equals(ops.get(0).getActorRole());
        });
    }

    private void requireModeReady() {
        if (agentModes != null && agentModes.selectList(null).stream().anyMatch(mode -> mode.getActiveTransitionId() != null))
            throw new IllegalArgumentException("平台正在切换模式，请等待环境准备完成");
        ActiveClassSessionRecord session = sessions.selectCurrent();
        if ((session == null || !Boolean.TRUE.equals(session.getActive())) && stoppingClass(environments.selectAllEnvironments()))
            throw new IllegalArgumentException("正在停止上一课堂环境，请等待完成后再上课");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> status() {
        ActiveClassSessionRecord session = sessions.selectCurrent();
        Map<String, Object> result = new LinkedHashMap<>();
        boolean active = session != null && Boolean.TRUE.equals(session.getActive());
        result.put("active", active);
        result.put("courseId", session == null ? null : session.getCourseId());
        result.put("environmentName", session == null ? null : session.getEnvironmentName());
        result.put("editorTool", session == null ? null : session.getEditorTool());
        List<TrainingEnvironmentRecord> rows = active ? selectedRows(session) : new ArrayList<>();
        long running = rows.stream().filter(row -> "RUNNING".equals(row.getActualState())).count();
        long failed = rows.stream().filter(row -> "ERROR".equals(row.getActualState()) || "DEGRADED".equals(row.getActualState())).count();
        result.put("totalCount", rows.size());
        result.put("runningCount", running);
        result.put("waitingCount", rows.size() - running - failed);
        result.put("failedCount", failed);
        result.put("state", !active ? "INACTIVE" : failed > 0 ? "FAILED" : running == rows.size() && !rows.isEmpty() ? "READY" : "STARTING");
        result.put("allReady", active && !rows.isEmpty() && running == rows.size());
        result.put("offlineServers", offlineServers(rows));
        List<TrainingEnvironmentRecord> all = environments.selectAllEnvironments();
        result.put("preparing", all.stream().anyMatch(row -> preparing(row.getActualState())));
        result.put("stopping", !active && stoppingClass(all));
        result.put("modeSwitching", agentModes != null && agentModes.selectList(null).stream().anyMatch(mode -> mode.getActiveTransitionId() != null));
        return result;
    }

    private List<TrainingEnvironmentRecord> selectedRows(ActiveClassSessionRecord session) {
        if (session.getCourseId() != null) return environments.selectByCourse(session.getCourseId());
        if (session.getEnvironmentName() != null) return environments.selectIndependentByName(session.getEnvironmentName());
        if (session.getEnvironmentId() != null) {
            TrainingEnvironmentRecord row = environments.selectById(session.getEnvironmentId());
            return row == null ? new ArrayList<>() : java.util.Collections.singletonList(row);
        }
        return new ArrayList<>();
    }

    private void requireOnlineOrIgnored(List<TrainingEnvironmentRecord> rows, boolean ignoreOffline) {
        List<Map<String, Object>> offline = offlineServers(rows);
        if (!ignoreOffline && !offline.isEmpty()) throw new ClassOfflineException(offline);
    }

    private List<Map<String, Object>> offlineServers(Collection<TrainingEnvironmentRecord> rows) {
        if (rows == null || rows.isEmpty()) return new ArrayList<>();
        Map<String, List<TrainingEnvironmentRecord>> byAgent = rows.stream()
                .collect(Collectors.groupingBy(TrainingEnvironmentRecord::getAgentId, LinkedHashMap::new, Collectors.toList()));
        Map<Integer, String> names = users.selectBatchIds(rows.stream().map(TrainingEnvironmentRecord::getUserId)
                .filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList())).stream()
                .collect(Collectors.toMap(User::getUserId, User::getUserName));
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(30);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<TrainingEnvironmentRecord>> entry : byAgent.entrySet()) {
            ProcessingAgentRecord agent = agents.selectForManagement(entry.getKey());
            boolean online = agent != null && Boolean.TRUE.equals(agent.getEnabled()) && agent.getRemovedAt() == null
                    && agent.getLastSeenAt() != null && agent.getLastSeenAt().isAfter(cutoff);
            if (online) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("agentId", entry.getKey());
            item.put("displayName", agent == null ? entry.getKey() : agent.getDisplayName());
            item.put("primaryIp", agent == null ? null : agent.getPrimaryIp());
            item.put("userIds", entry.getValue().stream().map(TrainingEnvironmentRecord::getUserId).collect(Collectors.toList()));
            item.put("userNames", entry.getValue().stream().map(row -> names.getOrDefault(row.getUserId(), String.valueOf(row.getUserId()))).collect(Collectors.toList()));
            result.add(item);
        }
        return result;
    }
    @Transactional public ActiveClassSessionRecord start(String environmentId, Integer actorId) {
        operationService.requireTrainingMode();
        TrainingEnvironmentRecord environment = environments.selectForUpdate(environmentId);
        if (environment == null || !"COURSE".equals(environment.getEnvironmentType()))
            throw new IllegalArgumentException("请选择课程实训环境");
        ActiveClassSessionRecord session = currentForUpdate();
        session.setEnvironmentId(environmentId); session.setEnvironmentName(environment.getEnvironmentName()); session.setCourseId(environment.getCourseId()); session.setEditorTool("VSCODE");
        session.setActive(true); session.setStartedBy(actorId); session.setStartedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now()); sessions.activate(environmentId, environment.getEnvironmentName(), environment.getCourseId(),
                session.getEditorTool(), actorId, session.getUpdatedAt()); return session;
    }
    @Transactional public ActiveClassSessionRecord stop(Integer actorId) {
        ActiveClassSessionRecord session = currentForUpdate();
        session.setActive(false); session.setEnvironmentId(null); session.setEnvironmentName(null); session.setCourseId(null); session.setEditorTool(null);
        session.setStartedBy(actorId); session.setUpdatedAt(LocalDateTime.now()); sessions.deactivate(actorId, session.getUpdatedAt());
        return session;
    }

    @Transactional public ActiveClassSessionRecord stopAndShutdown(Integer actorId, String role) {
        operationService.requireTrainingMode();
        ActiveClassSessionRecord session = stop(actorId);
        for (TrainingEnvironmentRecord row : environments.selectAllEnvironments()) {
            if (!"COMPETITION".equals(row.getEnvironmentType())
                    && ("RUNNING".equals(row.getDesiredState()) || "RUNNING".equals(row.getActualState())))
                operationService.stop(row.getEnvironmentId(), actorId, role);
        }
        return session;
    }

    private ActiveClassSessionRecord currentForUpdate() {
        sessions.ensureCurrent();
        ActiveClassSessionRecord session = sessions.selectCurrentForUpdate();
        if (session == null) {
            session = new ActiveClassSessionRecord();
            session.setSessionKey("CURRENT"); session.setActive(false); session.setUpdatedAt(LocalDateTime.now());
            sessions.insert(session);
        }
        return session;
    }
}
