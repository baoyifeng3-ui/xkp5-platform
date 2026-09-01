package com.match.resource.persistence;

import com.match.course.persistence.CourseResourceRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

public interface CourseResourceCatalogMapper {
    String PROJECTION = "SELECT f.file_id resource_id, l.course_id, l.resource_type, COALESCE(l.display_name,f.file_name) name, "
            + "l.chapter_id, l.practice_tool, f.storage_key, f.content_length, f.sha256, f.mime_type, l.sort_order, l.enabled, "
            + "f.uploaded_by created_by, f.created_at, GREATEST(f.updated_at,l.updated_at) updated_at "
            + "FROM course_resource_link l JOIN platform_file f ON f.file_id = l.file_id ";

    @Select(PROJECTION + "WHERE l.course_id = #{courseId} ORDER BY l.sort_order, f.created_at")
    List<CourseResourceRecord> selectByCourseId(@Param("courseId") String courseId);

    @Select(PROJECTION + "WHERE f.file_id = #{fileId} ORDER BY l.enabled DESC, l.created_at LIMIT 1")
    CourseResourceRecord selectByFileId(@Param("fileId") String fileId);

    @Select(PROJECTION + "WHERE l.course_id = #{courseId} AND f.file_id = #{fileId} LIMIT 1")
    CourseResourceRecord selectByCourseAndFile(@Param("courseId") String courseId,
                                               @Param("fileId") String fileId);

    @Select("SELECT course_id FROM course_resource_link WHERE file_id = #{fileId} AND enabled = 1")
    List<String> selectEnabledCourseIds(@Param("fileId") String fileId);
}
