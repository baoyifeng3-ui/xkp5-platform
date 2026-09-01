package com.match.environment.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface EnvironmentOperationMapper extends BaseMapper<EnvironmentOperationRecord> {
    @Select("SELECT * FROM environment_operation WHERE environment_id = #{environmentId} "
            + "AND active_operation_key IS NOT NULL LIMIT 1 FOR UPDATE")
    EnvironmentOperationRecord selectActive(@Param("environmentId") String environmentId);

    @Select("SELECT * FROM environment_operation WHERE operation_id = #{operationId} FOR UPDATE")
    EnvironmentOperationRecord selectForUpdate(@Param("operationId") String operationId);

    @Select("SELECT * FROM environment_operation WHERE command_id = #{commandId} FOR UPDATE")
    EnvironmentOperationRecord selectByCommandForUpdate(@Param("commandId") String commandId);

    @Select("SELECT * FROM environment_operation WHERE environment_id = #{environmentId} "
            + "ORDER BY requested_at DESC, operation_id DESC LIMIT #{limit}")
    List<EnvironmentOperationRecord> selectRecent(@Param("environmentId") String environmentId,
                                                   @Param("limit") int limit);

    @Select({"<script>",
            "SELECT o.* FROM environment_operation o WHERE o.environment_id IN",
            "<foreach collection='environmentIds' item='environmentId' open='(' separator=',' close=')'>",
            "#{environmentId}",
            "</foreach>",
            "AND NOT EXISTS (SELECT 1 FROM environment_operation newer",
            "WHERE newer.environment_id = o.environment_id AND",
            "(newer.requested_at &gt; o.requested_at OR",
            "(newer.requested_at = o.requested_at AND newer.operation_id &gt; o.operation_id)))",
            "ORDER BY o.environment_id",
            "</script>"})
    List<EnvironmentOperationRecord> selectLatestByEnvironments(
            @Param("environmentIds") List<String> environmentIds);

    @Update("UPDATE environment_operation SET state = #{state}, active_operation_key = NULL, "
            + "completed_at = #{completedAt}, result_code = #{resultCode}, "
            + "result_message = #{resultMessage}, component_results_json = #{componentResultsJson}, "
            + "updated_at = #{completedAt} WHERE operation_id = #{operationId} "
            + "AND state NOT IN ('SUCCEEDED', 'FAILED')")
    int markTerminal(@Param("operationId") String operationId,
                     @Param("state") String state,
                     @Param("completedAt") LocalDateTime completedAt,
                     @Param("resultCode") String resultCode,
                     @Param("resultMessage") String resultMessage,
                     @Param("componentResultsJson") String componentResultsJson);

    @Update("UPDATE environment_operation SET state='PENDING', command_id=#{commandId}, updated_at=#{updatedAt} "
            + "WHERE operation_id=#{operationId} AND state='WAITING_DEPENDENCY' AND command_id IS NULL")
    int dispatchWaiting(@Param("operationId") String operationId,
                        @Param("commandId") String commandId,
                        @Param("updatedAt") LocalDateTime updatedAt);
}
