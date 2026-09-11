package com.match.registry.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface ImageArtifactMapper extends BaseMapper<ImageArtifactRecord> {
    @Select("SELECT * FROM image_artifact WHERE group_id = #{groupId} AND component_type = #{componentType} AND version = #{version} ORDER BY updated_at DESC LIMIT 1 FOR UPDATE")
    ImageArtifactRecord selectByIdentityForUpdate(@Param("groupId") String groupId,
                                                   @Param("componentType") String componentType,
                                                   @Param("version") String version);
    @Select("SELECT * FROM image_artifact ORDER BY updated_at DESC, artifact_id DESC LIMIT #{limit}")
    List<ImageArtifactRecord> selectVisible(@Param("limit") int limit);
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

    @Select("SELECT * FROM image_artifact WHERE review_state = 'APPROVED' AND registry_digest IS NULL "
            + "AND (import_state IN ('NOT_IMPORTED', 'FAILED') OR "
            + "(import_state = 'IMPORTING' AND import_lease_expires_at < #{now})) "
            + "ORDER BY updated_at ASC LIMIT #{limit}")
    List<ImageArtifactRecord> selectImportCandidates(@Param("now") LocalDateTime now,
                                                     @Param("limit") int limit);

    @Update("UPDATE image_artifact SET import_state = 'IMPORTING', registry_digest = NULL, "
            + "import_attempt_token = #{attemptToken}, import_lease_expires_at = #{leaseExpiresAt}, "
            + "import_stage = 'VALIDATING', import_progress = 5, import_completed_layers = 0, "
            + "import_total_layers = 0, import_updated_at = #{now}, "
            + "failure_code = NULL, failure_message = NULL, updated_at = #{now} "
            + "WHERE artifact_id = #{artifactId} AND review_state = 'APPROVED' "
            + "AND registry_digest IS NULL AND (import_state IN ('NOT_IMPORTED', 'FAILED') OR "
            + "(import_state = 'IMPORTING' AND import_lease_expires_at < #{now}))")
    int claimImport(@Param("artifactId") String artifactId,
                    @Param("attemptToken") String attemptToken,
                    @Param("now") LocalDateTime now,
                    @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt);

    @Update("UPDATE image_artifact SET import_stage = #{stage}, import_progress = #{progress}, "
            + "import_completed_layers = #{completedLayers}, import_total_layers = #{totalLayers}, "
            + "import_updated_at = #{updatedAt}, updated_at = #{updatedAt} "
            + "WHERE artifact_id = #{artifactId} AND review_state = 'APPROVED' "
            + "AND import_state = 'IMPORTING' AND import_attempt_token = #{attemptToken} "
            + "AND registry_digest IS NULL")
    int updateImportProgress(@Param("artifactId") String artifactId,
                             @Param("attemptToken") String attemptToken,
                             @Param("stage") String stage,
                             @Param("progress") int progress,
                             @Param("completedLayers") int completedLayers,
                             @Param("totalLayers") int totalLayers,
                             @Param("updatedAt") LocalDateTime updatedAt);

    @Update("UPDATE image_artifact SET import_state = 'READY', registry_digest = #{registryDigest}, "
            + "import_attempt_token = NULL, import_lease_expires_at = NULL, "
            + "import_stage = 'COMPLETED', import_progress = 100, "
            + "import_completed_layers = import_total_layers, import_updated_at = #{importedAt}, "
            + "failure_code = NULL, failure_message = NULL, imported_at = #{importedAt}, "
            + "updated_at = #{importedAt} WHERE artifact_id = #{artifactId} "
            + "AND review_state = 'APPROVED' AND import_state = 'IMPORTING' "
            + "AND import_attempt_token = #{attemptToken} AND registry_digest IS NULL")
    int completeImport(@Param("artifactId") String artifactId,
                       @Param("attemptToken") String attemptToken,
                       @Param("registryDigest") String registryDigest,
                       @Param("importedAt") LocalDateTime importedAt);

    @Update("UPDATE image_artifact SET import_state = 'FAILED', failure_code = #{failureCode}, "
            + "failure_message = #{failureMessage}, import_attempt_token = NULL, "
            + "import_lease_expires_at = NULL, import_stage = 'FAILED', "
            + "import_updated_at = #{updatedAt}, updated_at = #{updatedAt} "
            + "WHERE artifact_id = #{artifactId} AND review_state = 'APPROVED' "
            + "AND import_state = 'IMPORTING' AND import_attempt_token = #{attemptToken} "
            + "AND registry_digest IS NULL")
    int failImport(@Param("artifactId") String artifactId,
                   @Param("attemptToken") String attemptToken,
                   @Param("failureCode") String failureCode,
                   @Param("failureMessage") String failureMessage,
                   @Param("updatedAt") LocalDateTime updatedAt);
}
