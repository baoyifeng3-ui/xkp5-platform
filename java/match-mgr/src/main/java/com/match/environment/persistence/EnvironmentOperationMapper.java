package com.match.environment.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface EnvironmentOperationMapper extends BaseMapper<EnvironmentOperationRecord> {
    @Select("SELECT * FROM environment_operation WHERE operation_id = #{operationId} FOR UPDATE")
    EnvironmentOperationRecord selectForUpdate(@Param("operationId") String operationId);

    @Select("SELECT * FROM environment_operation WHERE environment_id = #{environmentId} "
            + "ORDER BY requested_at DESC, operation_id DESC LIMIT #{limit}")
    List<EnvironmentOperationRecord> selectRecent(@Param("environmentId") String environmentId,
                                                   @Param("limit") int limit);

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
}
