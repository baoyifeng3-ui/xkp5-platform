package com.match.environment.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ContainerTemplateMapper extends BaseMapper<ContainerTemplateRecord> {
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
