package com.match.resource.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

public interface CourseResourceLinkMapper extends BaseMapper<CourseResourceLinkRecord> {
    @Select("SELECT * FROM course_resource_link WHERE course_id = #{courseId} ORDER BY sort_order, created_at")
    List<CourseResourceLinkRecord> selectByCourse(@Param("courseId") String courseId);

    @Select("SELECT * FROM course_resource_link WHERE file_id = #{fileId} ORDER BY created_at")
    List<CourseResourceLinkRecord> selectByFile(@Param("fileId") String fileId);

    @Select("SELECT * FROM course_resource_link WHERE course_id = #{courseId} AND file_id = #{fileId}")
    CourseResourceLinkRecord selectLink(@Param("courseId") String courseId,
                                        @Param("fileId") String fileId);

    @Delete("DELETE FROM course_resource_link WHERE course_id = #{courseId} AND file_id = #{fileId}")
    int deleteLink(@Param("courseId") String courseId, @Param("fileId") String fileId);
}
