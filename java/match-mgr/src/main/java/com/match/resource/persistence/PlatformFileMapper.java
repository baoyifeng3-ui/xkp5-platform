package com.match.resource.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

public interface PlatformFileMapper extends BaseMapper<PlatformFileRecord> {
    @Select("SELECT * FROM platform_file WHERE space_type = #{space} AND owner_user_id <=> #{ownerId} "
            + "AND directory_id <=> #{directoryId} ORDER BY file_name")
    List<PlatformFileRecord> selectEntries(@Param("space") String space,
                                           @Param("ownerId") Integer ownerId,
                                           @Param("directoryId") String directoryId);

    @Select("SELECT * FROM platform_file WHERE file_id = #{id} AND space_type = #{space} "
            + "AND owner_user_id <=> #{ownerId}")
    PlatformFileRecord selectScoped(@Param("id") String id, @Param("space") String space,
                                    @Param("ownerId") Integer ownerId);

    @Select("SELECT COUNT(*) FROM platform_file WHERE directory_id = #{directoryId}")
    int countInDirectory(@Param("directoryId") String directoryId);

    @Select("SELECT COALESCE(COUNT(*),0) file_count, COALESCE(SUM(content_length),0) total_bytes "
            + "FROM platform_file WHERE space_type = #{space}")
    Map<String, Object> summarizeSpace(@Param("space") String space);

    @Select("SELECT * FROM platform_file WHERE space_type = #{space} ORDER BY created_at")
    List<PlatformFileRecord> selectAllInSpace(@Param("space") String space);

    @Delete("DELETE FROM platform_file WHERE space_type = #{space}")
    int deleteBySpace(@Param("space") String space);
}
