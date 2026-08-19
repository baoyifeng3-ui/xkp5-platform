package com.match.terminal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.match.Application;
import com.match.agent.persistence.ProcessingAgentCommandMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.Test;
import org.mybatis.spring.annotation.MapperScan;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TerminalSessionSchemaTest {
    @Test
    public void migrationDefinesTerminalSessionsWithoutTranscriptStorage() throws Exception {
        String sql = read("/db/migration/V24__processing_agent_terminal_sessions.sql");

        assertTrue(sql.contains("CREATE TABLE processing_agent_terminal_session"));
        for (String column : new String[]{"session_id VARCHAR(36) NOT NULL",
                "agent_id VARCHAR(36) NOT NULL", "requester_user_id INT NOT NULL",
                "requester_role VARCHAR(32) NOT NULL", "state VARCHAR(24) NOT NULL",
                "active_agent_id VARCHAR(36) NULL", "agent_ticket_digest CHAR(64) NULL",
                "agent_ticket_expires_at DATETIME(3) NULL", "agent_ticket_consumed_at DATETIME(3) NULL",
                "browser_ticket_digest CHAR(64) NULL", "browser_ticket_expires_at DATETIME(3) NULL",
                "browser_ticket_consumed_at DATETIME(3) NULL", "command_id VARCHAR(36) NULL",
                "requested_at DATETIME(3) NOT NULL", "agent_connected_at DATETIME(3) NULL",
                "browser_connected_at DATETIME(3) NULL", "active_at DATETIME(3) NULL",
                "last_io_at DATETIME(3) NULL", "absolute_expires_at DATETIME(3) NOT NULL",
                "ended_at DATETIME(3) NULL", "end_reason VARCHAR(64) NULL",
                "end_message VARCHAR(512) NULL", "browser_to_agent_bytes BIGINT UNSIGNED NOT NULL DEFAULT 0",
                "agent_to_browser_bytes BIGINT UNSIGNED NOT NULL DEFAULT 0",
                "updated_at DATETIME(3) NOT NULL"}) {
            assertTrue("missing terminal session column " + column, sql.contains(column));
        }

        assertUniqueIndex(sql, "uk_terminal_active_agent", "active_agent_id");
        assertUniqueIndex(sql, "uk_terminal_command_id", "command_id");
        assertIndex(sql, "idx_terminal_state_expiry", "state", "absolute_expires_at");
        assertIndex(sql, "idx_terminal_agent_history", "agent_id", "requested_at");
        assertFalse(sql.contains("terminal_input"));
        assertFalse(sql.contains("terminal_output"));
    }

    @Test
    public void recordMapsEveryTerminalSessionColumn() throws Exception {
        Class<?> record = load("com.match.terminal.persistence.TerminalSessionRecord");

        assertEquals("processing_agent_terminal_session",
                record.getAnnotation(TableName.class).value());
        Field id = record.getDeclaredField("sessionId");
        assertEquals("session_id", id.getAnnotation(TableId.class).value());

        Set<String> fields = new HashSet<>();
        for (Field field : record.getDeclaredFields()) {
            fields.add(field.getName());
        }
        assertTrue(fields.containsAll(Arrays.asList("sessionId", "agentId", "requesterUserId",
                "requesterRole", "state", "activeAgentId", "agentTicketDigest",
                "agentTicketExpiresAt", "agentTicketConsumedAt", "browserTicketDigest",
                "browserTicketExpiresAt", "browserTicketConsumedAt", "commandId", "requestedAt",
                "agentConnectedAt", "browserConnectedAt", "activeAt", "lastIoAt",
                "absoluteExpiresAt", "endedAt", "endReason", "endMessage",
                "browserToAgentBytes", "agentToBrowserBytes", "updatedAt")));
    }

    @Test
    public void mapperExposesAtomicStateGuardedLifecycleOperations() throws Exception {
        Class<?> mapper = load("com.match.terminal.persistence.TerminalSessionMapper");
        assertMethod(mapper, "selectById", 1, Select.class);
        assertMethod(mapper, "selectActiveByAgent", 1, Select.class);
        assertMethod(mapper, "selectByCommandId", 1, Select.class);
        assertMethod(mapper, "insert", 1, Insert.class);

        String setCommand = sql(assertMethod(mapper, "setCommand", 3, Update.class));
        assertContainsAll(setCommand, "state = 'WAITING_AGENT'", "command_id IS NULL",
                "absolute_expires_at > #{now}");

        String issueAgent = sql(assertMethod(mapper, "issueAgentTicket", 4, Update.class));
        assertContainsAll(issueAgent, "state = 'WAITING_AGENT'", "agent_ticket_digest = #{digest}",
                "agent_ticket_consumed_at = NULL", "absolute_expires_at > #{now}");

        String consumeAgent = sql(assertMethod(mapper, "consumeAgentTicket", 4, Update.class));
        assertContainsAll(consumeAgent, "state = 'WAITING_AGENT'", "agent_ticket_digest = #{digest}",
                "agent_ticket_consumed_at IS NULL", "agent_ticket_expires_at > #{now}",
                "absolute_expires_at > #{now}", "state = 'WAITING_BROWSER'",
                "BINARY s.agent_id = BINARY #{agentId}",
                "JOIN processing_agent", "BINARY a.agent_id = BINARY #{agentId}",
                "a.enabled = TRUE", "a.removed_at IS NULL", "a.last_seen_at IS NOT NULL",
                "a.last_seen_at >= DATE_SUB(#{now}, INTERVAL 15 SECOND)",
                "a.last_seen_at <= #{now}");

        String issueBrowser = sql(assertMethod(mapper, "issueBrowserTicket", 4, Update.class));
        assertContainsAll(issueBrowser, "state = 'WAITING_BROWSER'",
                "browser_ticket_digest = #{digest}",
                "browser_ticket_consumed_at IS NULL", "browser_connected_at IS NULL",
                "absolute_expires_at > #{now}");
        assertFalse(issueBrowser.contains("browser_ticket_consumed_at = NULL"));
        assertFalse(issueBrowser.contains("WAITING_AGENT"));

        String consumeBrowser = sql(assertMethod(mapper, "consumeBrowserTicket", 3, Update.class));
        assertContainsAll(consumeBrowser, "state = 'WAITING_BROWSER'",
                "browser_ticket_digest = #{digest}", "browser_ticket_consumed_at IS NULL",
                "browser_ticket_expires_at > #{now}", "absolute_expires_at > #{now}");
        assertFalse(consumeBrowser.contains("WAITING_AGENT"));

        String markActive = sql(assertMethod(mapper, "markActive", 2, Update.class));
        assertContainsAll(markActive, "state = 'WAITING_BROWSER'",
                "agent_connected_at IS NOT NULL", "browser_connected_at IS NOT NULL",
                "agent_ticket_consumed_at IS NOT NULL", "browser_ticket_consumed_at IS NOT NULL",
                "absolute_expires_at > #{now}");
        assertFalse(markActive.contains("WAITING_AGENT"));

        String setTraffic = sql(assertMethod(mapper, "setTrafficTotals", 4, Update.class));
        assertContainsAll(setTraffic, "state = 'ACTIVE'",
                "GREATEST(browser_to_agent_bytes, #{browserToAgentTotal})",
                "GREATEST(agent_to_browser_bytes, #{agentToBrowserTotal})",
                "absolute_expires_at > #{ioAt}");

        String close = sql(assertMethod(mapper, "close", 5, Update.class));
        assertContainsAll(close, "active_agent_id = NULL",
                "state IN ('WAITING_AGENT', 'WAITING_BROWSER', 'ACTIVE')",
                "#{state} IN ('CLOSED', 'FAILED')");
        assertFalse(close.contains("'EXPIRED'"));

        String expired = sql(assertMethod(mapper, "selectExpired", 3, Select.class));
        assertContainsAll(expired, "state IN ('WAITING_AGENT', 'WAITING_BROWSER', 'ACTIVE')",
                "absolute_expires_at <= #{now}", "last_io_at", "#{idleBefore}",
                "ORDER BY absolute_expires_at, requested_at, session_id", "LIMIT #{limit}");
    }

    @Test
    public void mapperDoesNotExposeGenericMutationApis() {
        Class<?> mapper = load("com.match.terminal.persistence.TerminalSessionMapper");

        assertFalse(BaseMapper.class.isAssignableFrom(mapper));
    }

    @Test
    public void ticketsCannotOutliveTheirConnectionPhases() {
        Class<?> mapper = load("com.match.terminal.persistence.TerminalSessionMapper");

        String issueAgent = sql(assertMethod(mapper, "issueAgentTicket", 4, Update.class));
        assertContainsAll(issueAgent, "DATE_ADD(requested_at, INTERVAL 90 SECOND) > #{now}",
                "#{expiresAt} <= LEAST(DATE_ADD(requested_at, INTERVAL 90 SECOND), absolute_expires_at)");

        String consumeAgent = sql(assertMethod(mapper, "consumeAgentTicket", 4, Update.class));
        assertContainsAll(consumeAgent, "DATE_ADD(s.requested_at, INTERVAL 90 SECOND) > #{now}",
                "s.agent_ticket_expires_at > #{now}", "BINARY s.agent_id = BINARY #{agentId}");

        String issueBrowser = sql(assertMethod(mapper, "issueBrowserTicket", 4, Update.class));
        assertContainsAll(issueBrowser, "DATE_ADD(agent_connected_at, INTERVAL 60 SECOND) > #{now}",
                "#{expiresAt} <= LEAST(DATE_ADD(agent_connected_at, INTERVAL 60 SECOND), absolute_expires_at)");

        String consumeBrowser = sql(assertMethod(mapper, "consumeBrowserTicket", 3, Update.class));
        assertContainsAll(consumeBrowser, "DATE_ADD(agent_connected_at, INTERVAL 60 SECOND) > #{now}",
                "browser_ticket_expires_at > #{now}");
    }

    @Test
    public void lowercaseOrMixedCaseStateAndTypeCannotAuthorizeTerminalLease() {
        String query = sql(assertMethod(ProcessingAgentCommandMapper.class,
                "selectRunningTerminalLeaseForUpdate", 4, Select.class));

        assertContainsAll(query, "BINARY state = BINARY 'RUNNING'",
                "BINARY command_type = BINARY 'OPEN_ROOT_TERMINAL'",
                "BINARY command_id = BINARY #{commandId}",
                "BINARY agent_id = BINARY #{agentId}",
                "BINARY lease_token = BINARY #{leaseToken}",
                "BINARY JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.sessionId')) = BINARY #{sessionId}",
                "LIMIT 1 FOR UPDATE");
    }

    @Test
    public void trafficFlushesCannotMoveTimestampsBackward() {
        Class<?> mapper = load("com.match.terminal.persistence.TerminalSessionMapper");

        String setTraffic = sql(assertMethod(mapper, "setTrafficTotals", 4, Update.class));
        assertContainsAll(setTraffic,
                "CASE WHEN #{browserToAgentTotal} > browser_to_agent_bytes",
                "#{agentToBrowserTotal} > agent_to_browser_bytes",
                "THEN GREATEST(COALESCE(last_io_at, active_at, #{ioAt}), #{ioAt})",
                "ELSE last_io_at END",
                "updated_at = GREATEST(updated_at, #{ioAt})",
                "#{browserToAgentTotal} >= 0", "#{agentToBrowserTotal} >= 0");
    }

    @Test
    public void expiredClosureAtomicallyRechecksEveryExpiryCondition() {
        Class<?> mapper = load("com.match.terminal.persistence.TerminalSessionMapper");

        String closeExpired = sql(assertMethod(mapper, "closeExpired", 6, Update.class));
        assertContainsAll(closeExpired, "active_agent_id = NULL", "agent_ticket_digest = NULL",
                "browser_ticket_digest = NULL", "#{state} IN ('CLOSED', 'FAILED')",
                "state IN ('WAITING_AGENT', 'WAITING_BROWSER', 'ACTIVE')",
                "absolute_expires_at <= #{now}",
                "state = 'WAITING_AGENT' AND requested_at <= DATE_SUB(#{now}, INTERVAL 90 SECOND)",
                "state = 'WAITING_BROWSER' AND agent_connected_at <= DATE_SUB(#{now}, INTERVAL 60 SECOND)",
                "state = 'ACTIVE' AND COALESCE(last_io_at, active_at) <= #{idleBefore}");
        assertFalse(closeExpired.contains("'EXPIRED'"));
    }

    @Test
    public void recoveryAndCommandReconciliationAreGuardedAndReleaseSecrets() {
        Class<?> mapper = load("com.match.terminal.persistence.TerminalSessionMapper");
        String recoverable = sql(assertMethod(mapper, "selectRecoverable", 1, Select.class));
        assertContainsAll(recoverable,
                "state IN ('WAITING_AGENT', 'WAITING_BROWSER', 'ACTIVE')",
                "ORDER BY requested_at, session_id", "LIMIT #{limit}");

        String recover = sql(assertMethod(mapper, "recover", 2, Update.class));
        assertContainsAll(recover, "state = 'FAILED'", "active_agent_id = NULL",
                "agent_ticket_digest = NULL", "browser_ticket_digest = NULL",
                "end_reason = 'MANAGEMENT_RESTARTED'",
                "state IN ('WAITING_AGENT', 'WAITING_BROWSER', 'ACTIVE')");

        String command = sql(assertMethod(mapper, "closeCommandSession", 7, Update.class));
        assertContainsAll(command, "BINARY command_id = BINARY #{commandId}",
                "BINARY agent_id = BINARY #{agentId}", "active_agent_id = NULL",
                "agent_ticket_digest = NULL", "browser_ticket_digest = NULL",
                "state IN ('WAITING_AGENT', 'WAITING_BROWSER', 'ACTIVE')");

        String candidates = sql(assertMethod(mapper,
                "selectCommandReconciliationCandidates", 1, Select.class));
        assertContainsAll(candidates,
                "BINARY c.command_id = BINARY s.command_id",
                "BINARY c.command_type = BINARY 'OPEN_ROOT_TERMINAL'",
                "c.state IN ('SUCCEEDED', 'FAILED')",
                "s.state IN ('WAITING_AGENT', 'WAITING_BROWSER', 'ACTIVE')", "LIMIT #{limit}");
    }

    @Test
    public void applicationScansTerminalMappers() {
        MapperScan mapperScan = Application.class.getAnnotation(MapperScan.class);

        assertNotNull(mapperScan);
        assertTrue(Arrays.asList(mapperScan.value()).contains("com.match.terminal.persistence"));
    }

    private Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException exception) {
            fail("missing class " + name);
            return null;
        }
    }

    private Method assertMethod(Class<?> type, String name, int parameterCount,
                                Class<? extends java.lang.annotation.Annotation> annotation) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                assertNotNull("missing @" + annotation.getSimpleName() + " on " + name,
                        method.getAnnotation(annotation));
                return method;
            }
        }
        fail("missing mapper method " + name + " with " + parameterCount + " parameters");
        return null;
    }

    private String sql(Method method) {
        Select select = method.getAnnotation(Select.class);
        if (select != null) {
            return String.join(" ", select.value());
        }
        Update update = method.getAnnotation(Update.class);
        if (update != null) {
            return String.join(" ", update.value());
        }
        Insert insert = method.getAnnotation(Insert.class);
        return String.join(" ", insert.value());
    }

    private void assertContainsAll(String sql, String... fragments) {
        for (String fragment : fragments) {
            assertTrue("missing SQL guard " + fragment, sql.contains(fragment));
        }
    }

    private void assertUniqueIndex(String sql, String name, String... columns) {
        assertTrue(sql.contains("UNIQUE KEY " + name + " (" + String.join(", ", columns) + ")"));
    }

    private void assertIndex(String sql, String name, String... columns) {
        assertTrue(sql.contains("KEY " + name + " (" + String.join(", ", columns) + ")"));
    }

    private String read(String path) throws IOException {
        InputStream input = getClass().getResourceAsStream(path);
        assertNotNull("missing migration " + path, input);
        try (InputStream closeable = input) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = closeable.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
