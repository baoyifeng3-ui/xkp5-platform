package com.match.registry.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ImageUploadMapper extends BaseMapper<ImageUploadRecord> {
    @Select("SELECT * FROM image_upload WHERE upload_id = #{uploadId} FOR UPDATE")
    ImageUploadRecord selectForUpdate(@Param("uploadId") String uploadId);

    @Select("SELECT * FROM image_upload WHERE artifact_id = #{artifactId} LIMIT 1")
    ImageUploadRecord selectByArtifactId(@Param("artifactId") String artifactId);

    @Update("UPDATE image_upload SET state = #{state}, final_sha256 = #{finalSha256}, "
            + "completed_at = #{completedAt}, updated_at = #{updatedAt} "
            + "WHERE upload_id = #{uploadId} AND state = 'UPLOADING'")
    int complete(@Param("uploadId") String uploadId,
                 @Param("state") String state,
                 @Param("finalSha256") String finalSha256,
                 @Param("completedAt") java.time.LocalDateTime completedAt,
                 @Param("updatedAt") java.time.LocalDateTime updatedAt);
}
