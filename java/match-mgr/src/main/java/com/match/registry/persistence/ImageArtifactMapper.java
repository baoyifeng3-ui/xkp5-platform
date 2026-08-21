package com.match.registry.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ImageArtifactMapper extends BaseMapper<ImageArtifactRecord> {
    @Select("SELECT * FROM image_artifact WHERE artifact_id = #{artifactId} "
            + "AND review_state = 'PENDING_REVIEW' FOR UPDATE")
    ImageArtifactRecord selectPendingReviewForUpdate(@Param("artifactId") String artifactId);

    @Update("UPDATE image_artifact SET import_state = #{importState}, registry_digest = #{registryDigest}, "
            + "failure_code = #{failureCode}, failure_message = #{failureMessage}, imported_at = #{importedAt}, "
            + "updated_at = #{updatedAt} WHERE artifact_id = #{artifactId} AND review_state = 'APPROVED'")
    int updateImportResult(@Param("artifactId") String artifactId,
                           @Param("importState") String importState,
                           @Param("registryDigest") String registryDigest,
                           @Param("failureCode") String failureCode,
                           @Param("failureMessage") String failureMessage,
                           @Param("importedAt") java.time.LocalDateTime importedAt,
                           @Param("updatedAt") java.time.LocalDateTime updatedAt);
}
