package com.match.resource.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

public interface ResourceDirectoryMapper extends BaseMapper<ResourceDirectoryRecord> {
    @Select("SELECT * FROM resource_directory WHERE space_type = #{space} AND owner_user_id <=> #{ownerId} "
            + "AND parent_id <=> #{parentId} ORDER BY name")
    List<ResourceDirectoryRecord> selectChildren(@Param("space") String space,
                                                 @Param("ownerId") Integer ownerId,
                                                 @Param("parentId") String parentId);

    @Select("SELECT * FROM resource_directory WHERE directory_id = #{id} AND space_type = #{space} "
            + "AND owner_user_id <=> #{ownerId}")
    ResourceDirectoryRecord selectScoped(@Param("id") String id, @Param("space") String space,
                                         @Param("ownerId") Integer ownerId);

    @Select("SELECT COUNT(*) FROM resource_directory WHERE parent_id = #{id}")
    int countChildDirectories(@Param("id") String id);

    @Delete("DELETE FROM resource_directory WHERE space_type = #{space}")
    int deleteBySpace(@Param("space") String space);
}
