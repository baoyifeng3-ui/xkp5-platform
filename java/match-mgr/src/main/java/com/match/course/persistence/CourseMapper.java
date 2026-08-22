package com.match.course.persistence;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
public interface CourseMapper extends BaseMapper<CourseRecord> {
    @Select("SELECT * FROM course WHERE course_id = #{courseId} FOR UPDATE") CourseRecord selectForUpdate(String courseId);
    @Select("SELECT * FROM course WHERE enabled = 1 ORDER BY updated_at DESC") List<CourseRecord> selectVisible();
}
