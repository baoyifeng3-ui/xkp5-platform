package com.match.mode.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface PlatformModeMapper extends BaseMapper<PlatformModeRecord> {
    @Select("SELECT singleton_id, mode, generation, changed_by, changed_at "
            + "FROM platform_mode WHERE singleton_id = 1")
    PlatformModeRecord selectCurrent();

    @Select("SELECT singleton_id, mode, generation, changed_by, changed_at "
            + "FROM platform_mode WHERE singleton_id = 1 FOR UPDATE")
    PlatformModeRecord selectForUpdate();

    @Update("UPDATE platform_mode SET mode = #{targetMode}, generation = #{nextGeneration}, "
            + "changed_by = #{actorUserId}, changed_at = #{changedAt} "
            + "WHERE singleton_id = 1 AND mode = #{expectedMode} "
            + "AND generation = #{expectedGeneration}")
    int compareAndSet(@Param("expectedMode") String expectedMode,
                      @Param("expectedGeneration") long expectedGeneration,
                      @Param("targetMode") String targetMode,
                      @Param("nextGeneration") long nextGeneration,
                      @Param("actorUserId") int actorUserId,
                      @Param("changedAt") LocalDateTime changedAt);
}
