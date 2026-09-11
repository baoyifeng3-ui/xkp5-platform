package com.match.registry.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface ImageReleaseMapper extends BaseMapper<ImageReleaseRecord> {
    @Select("SELECT * FROM image_release ORDER BY published_at DESC, release_id DESC LIMIT #{limit}")
    List<ImageReleaseRecord> selectVisible(@Param("limit") int limit);
    @Select("SELECT * FROM image_release WHERE artifact_id = #{artifactId}")
    List<ImageReleaseRecord> selectByArtifactId(@Param("artifactId") String artifactId);
}
