package com.match.environment.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ContainerTemplateMapper extends BaseMapper<ContainerTemplateRecord> {
    @Select("SELECT COUNT(*) FROM container_template WHERE BINARY image_reference=BINARY #{reference} OR BINARY image_id=BINARY #{imageId}")
    int countImageReferences(@Param("reference") String reference, @Param("imageId") String imageId);
    @Select("SELECT registry_digest FROM image_release WHERE release_id = #{releaseId} AND component_type = #{componentType} AND state = 'PUBLISHED'")
    String selectPublishedDigest(@Param("releaseId") String releaseId, @Param("componentType") String componentType);
    @Select("SELECT CONCAT(a.image_repository, ':', a.image_tag) FROM image_release r "
            + "JOIN image_artifact a ON BINARY a.artifact_id=BINARY r.artifact_id "
            + "WHERE r.release_id=#{releaseId} AND r.component_type=#{componentType} "
            + "AND r.state='PUBLISHED' AND a.image_repository IS NOT NULL AND a.image_tag IS NOT NULL")
    String selectPublishedImageReference(@Param("releaseId") String releaseId,@Param("componentType") String componentType);
    @Select("SELECT COUNT(1) FROM training_environment WHERE (annotation_template_id = #{templateId} AND annotation_template_version = #{version}) OR (editor_template_id = #{templateId} AND editor_template_version = #{version})")
    int countTrainingReferences(@Param("templateId") String templateId, @Param("version") int version);

    @Select("SELECT COUNT(1) FROM competition_environment WHERE (annotation_template_id = #{templateId} AND annotation_template_version = #{version}) OR (editor_template_id = #{templateId} AND editor_template_version = #{version})")
    int countCompetitionReferences(@Param("templateId") String templateId, @Param("version") int version);
    @Select("SELECT * FROM container_template WHERE template_id = #{templateId} "
            + "AND template_version = #{version}")
    ContainerTemplateRecord selectVersion(@Param("templateId") String templateId,
                                           @Param("version") int version);

    @Select("SELECT * FROM container_template WHERE template_id = #{templateId} "
            + "ORDER BY template_version DESC")
    List<ContainerTemplateRecord> selectVersions(@Param("templateId") String templateId);

    @Select("SELECT * FROM container_template WHERE template_id = #{templateId} "
            + "ORDER BY template_version DESC LIMIT 1 FOR UPDATE")
    ContainerTemplateRecord selectLatestForUpdate(@Param("templateId") String templateId);

    @Select("SELECT * FROM container_template ORDER BY template_id, template_version")
    List<ContainerTemplateRecord> selectAllVersions();
}
