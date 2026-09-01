package com.match.course.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

public interface CourseChapterMapper extends BaseMapper<CourseChapterRecord> {
    @Select("SELECT * FROM course_chapter WHERE course_id=#{courseId} ORDER BY sort_order,created_at") List<CourseChapterRecord> selectByCourse(@Param("courseId") String courseId);
}
