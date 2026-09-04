package com.match.environment.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;

public interface ActiveClassSessionMapper extends BaseMapper<ActiveClassSessionRecord> {
    @Insert("INSERT IGNORE INTO active_class_session(session_key, active, updated_at) VALUES ('CURRENT', 0, UTC_TIMESTAMP(3))")
    int ensureCurrent();
    @Select("SELECT * FROM active_class_session WHERE session_key = 'CURRENT' FOR UPDATE")
    ActiveClassSessionRecord selectCurrentForUpdate();
    @Select("SELECT * FROM active_class_session WHERE session_key = 'CURRENT'")
    ActiveClassSessionRecord selectCurrent();

    @Update("UPDATE active_class_session SET environment_id=#{environmentId}, environment_name=#{environmentName}, course_id=#{courseId}, "
            + "editor_tool=#{editorTool}, active=1, started_by=#{actorId}, started_at=#{now}, updated_at=#{now} "
            + "WHERE session_key='CURRENT'")
    int activate(@Param("environmentId") String environmentId, @Param("environmentName") String environmentName,
                 @Param("courseId") String courseId,
                 @Param("editorTool") String editorTool, @Param("actorId") Integer actorId,
                 @Param("now") LocalDateTime now);

    @Update("UPDATE active_class_session SET environment_id=NULL, environment_name=NULL, course_id=NULL, editor_tool=NULL, "
            + "active=0, started_by=#{actorId}, updated_at=#{now} WHERE session_key='CURRENT'")
    int deactivate(@Param("actorId") Integer actorId, @Param("now") LocalDateTime now);
}
