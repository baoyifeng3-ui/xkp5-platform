package com.match.course.persistence;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface CourseResourceMapper extends BaseMapper<CourseResourceRecord> {
    @Select("SELECT * FROM course_resource WHERE enabled = 1 AND course_id IN (SELECT course_id FROM course WHERE enabled = 1) ORDER BY course_id, sort_order")
    List<CourseResourceRecord> selectVisible(Integer userId);

    @Select("SELECT * FROM course_resource WHERE course_id = #{courseId} ORDER BY sort_order, created_at")
    List<CourseResourceRecord> selectByCourseId(@Param("courseId") String courseId);

    @Select("SELECT * FROM course_resource WHERE course_id = #{courseId} AND sha256 = #{sha256} LIMIT 1")
    CourseResourceRecord selectByDigest(@Param("courseId") String courseId,
                                        @Param("sha256") String sha256);
}
