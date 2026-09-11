package com.match.registry.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ImageFileMapper extends BaseMapper<ImageFileRecord> {
    @Select("SELECT * FROM image_file WHERE BINARY CONCAT(image_repository, ':', image_tag)=BINARY #{reference} LIMIT 1")
    ImageFileRecord selectByImageReference(@Param("reference") String reference);

    @Select("SELECT COUNT(*) FROM container_template t WHERE image_file_id=#{fileId} OR EXISTS "
            + "(SELECT 1 FROM image_deployment d WHERE d.file_id=#{fileId} AND d.state='SUCCEEDED' AND BINARY d.target_digest=BINARY t.image_id)")
    int countFileTemplateReferences(@Param("fileId") String fileId);
    @Select("SELECT * FROM image_file WHERE BINARY original_filename=BINARY #{filename} ORDER BY created_at, file_id LIMIT 1")
    ImageFileRecord selectByFilename(@Param("filename") String filename);

    @Select("SELECT COUNT(*) FROM container_template WHERE BINARY image_reference=BINARY #{reference}")
    int countTemplateReferences(@Param("reference") String reference);

    @Select("SELECT * FROM image_file WHERE image_repository=#{repository} AND original_filename=#{filename} LIMIT 1")
    ImageFileRecord selectByIdentity(@Param("repository") String repository, @Param("filename") String filename);

    @Select("SELECT * FROM image_file WHERE enabled=1 ORDER BY updated_at DESC LIMIT #{limit}")
    List<ImageFileRecord> selectEnabled(@Param("limit") int limit);
}
