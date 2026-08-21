package com.match.registry.persistence;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface ImageUploadChunkMapper {
    @Select("SELECT * FROM image_upload_chunk WHERE upload_id = #{uploadId} "
            + "AND chunk_index = #{chunkIndex} FOR UPDATE")
    ImageUploadChunkRecord selectForUpdate(@Param("uploadId") String uploadId,
                                           @Param("chunkIndex") int chunkIndex);

    @Select("SELECT * FROM image_upload_chunk WHERE upload_id = #{uploadId} "
            + "ORDER BY chunk_index")
    List<ImageUploadChunkRecord> selectByUpload(@Param("uploadId") String uploadId);

    @Select("SELECT chunk_sha256 FROM image_upload_chunk WHERE upload_id = #{uploadId} "
            + "AND chunk_index = #{chunkIndex} FOR UPDATE")
    String selectExistingChecksumForUpdate(@Param("uploadId") String uploadId,
                                           @Param("chunkIndex") int chunkIndex);

    /** Idempotent retry for an identical checksum; callers compare the existing checksum before retrying. */
    @Insert("INSERT INTO image_upload_chunk (upload_id, chunk_index, chunk_size, chunk_sha256, "
            + "stored_bytes, created_at) VALUES (#{uploadId}, #{chunkIndex}, #{chunkSize}, "
            + "#{chunkSha256}, #{storedBytes}, #{createdAt}) "
            + "ON DUPLICATE KEY UPDATE chunk_size = IF(chunk_sha256 = VALUES(chunk_sha256), "
            + "VALUES(chunk_size), chunk_size), stored_bytes = IF(chunk_sha256 = VALUES(chunk_sha256), "
            + "VALUES(stored_bytes), stored_bytes)")
    int insertOrRetry(@Param("uploadId") String uploadId,
                      @Param("chunkIndex") int chunkIndex,
                      @Param("chunkSize") int chunkSize,
                      @Param("chunkSha256") String chunkSha256,
                      @Param("storedBytes") long storedBytes,
                      @Param("createdAt") LocalDateTime createdAt);

    @Delete("DELETE FROM image_upload_chunk WHERE upload_id = #{uploadId}")
    int deleteByUpload(@Param("uploadId") String uploadId);
}
