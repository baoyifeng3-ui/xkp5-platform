package com.match.registry.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

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

    @Select("SELECT * FROM image_artifact WHERE review_state = 'APPROVED' AND registry_digest IS NULL "
            + "AND (import_state IN ('NOT_IMPORTED', 'FAILED') OR "
            + "(import_state = 'IMPORTING' AND import_lease_expires_at < #{now})) "
            + "ORDER BY updated_at ASC LIMIT #{limit}")
    List<ImageArtifactRecord> selectImportCandidates(@Param("now") LocalDateTime now,
                                                     @Param("limit") int limit);

    @Update("UPDATE image_artifact SET import_state = 'IMPORTING', registry_digest = NULL, "
            + "import_attempt_token = #{attemptToken}, import_lease_expires_at = #{leaseExpiresAt}, "
            + "failure_code = NULL, failure_message = NULL, updated_at = #{now} "
            + "WHERE artifact_id = #{artifactId} AND review_state = 'APPROVED' "
            + "AND registry_digest IS NULL AND (import_state IN ('NOT_IMPORTED', 'FAILED') OR "
            + "(import_state = 'IMPORTING' AND import_lease_expires_at < #{now}))")
    int claimImport(@Param("artifactId") String artifactId,
                    @Param("attemptToken") String attemptToken,
                    @Param("now") LocalDateTime now,
                    @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt);

    @Update("UPDATE image_artifact SET import_state = 'READY', registry_digest = #{registryDigest}, "
            + "import_attempt_token = NULL, import_lease_expires_at = NULL, "
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
            + "import_lease_expires_at = NULL, updated_at = #{updatedAt} "
            + "WHERE artifact_id = #{artifactId} AND review_state = 'APPROVED' "
            + "AND import_state = 'IMPORTING' AND import_attempt_token = #{attemptToken} "
            + "AND registry_digest IS NULL")
    int failImport(@Param("artifactId") String artifactId,
                   @Param("attemptToken") String attemptToken,
                   @Param("failureCode") String failureCode,
                   @Param("failureMessage") String failureMessage,
                   @Param("updatedAt") LocalDateTime updatedAt);
}
