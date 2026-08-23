package com.match.dashboard.persistence;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface DashboardOverviewMapper {
    @Select("SELECT COUNT(*) FROM processing_agent WHERE enabled = 1 AND removed_at IS NULL")
    int countEnabledAgents();

    @Select("SELECT COUNT(*) FROM processing_agent WHERE enabled = 1 AND removed_at IS NULL "
            + "AND last_seen_at >= #{cutoff}")
    int countOnlineAgents(@Param("cutoff") LocalDateTime cutoff);

    @Select("SELECT COUNT(*) FROM processing_agent a LEFT JOIN processing_agent_mode m "
            + "ON m.agent_id = a.agent_id WHERE a.enabled = 1 AND a.removed_at IS NULL "
            + "AND BINARY COALESCE(m.actual_mode, 'NORMAL') = BINARY #{state}")
    int countAgentModesInState(@Param("state") String state);

    @Select("SELECT latest_metrics FROM processing_agent WHERE enabled = 1 AND removed_at IS NULL "
            + "AND last_seen_at >= #{cutoff} AND latest_metrics IS NOT NULL ORDER BY agent_id")
    List<String> selectOnlineMetricJson(@Param("cutoff") LocalDateTime cutoff);

    @Select("SELECT COUNT(*) FROM training_environment WHERE actual_state = #{state}")
    int countEnvironmentsInState(@Param("state") String state);

    @Select("SELECT COUNT(*) FROM training_environment WHERE actual_state IN "
            + "('CREATING', 'STARTING', 'STOPPING', 'RESTORING', 'WAITING_DEPENDENCY')")
    int countTransitionalEnvironments();

    @Select("SELECT COUNT(*) FROM environment_operation WHERE state IN ('PENDING', 'RUNNING')")
    int countActiveOperations();

    @Select("SELECT COUNT(*) FROM environment_operation failed WHERE failed.state = 'FAILED' "
            + "AND NOT EXISTS (SELECT 1 FROM environment_operation succeeded "
            + "WHERE succeeded.environment_id = failed.environment_id AND succeeded.state = 'SUCCEEDED' "
            + "AND succeeded.requested_at > failed.requested_at)")
    int countFailedOperations();

    @Select("SELECT COUNT(*) FROM processing_agent_command WHERE state IN ('PENDING', 'LEASED', 'RUNNING')")
    int countActiveCommands();

    @Select("SELECT COUNT(*) FROM processing_agent_command failed WHERE failed.state = 'FAILED' "
            + "AND NOT EXISTS (SELECT 1 FROM processing_agent_command succeeded "
            + "WHERE succeeded.agent_id = failed.agent_id AND succeeded.command_type = failed.command_type "
            + "AND succeeded.state = 'SUCCEEDED' AND succeeded.requested_at > failed.requested_at)")
    int countFailedCommands();
}
