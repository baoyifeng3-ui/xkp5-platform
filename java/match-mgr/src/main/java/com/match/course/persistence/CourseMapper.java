package com.match.course.persistence;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
public interface CourseMapper extends BaseMapper<CourseRecord> { @Select("SELECT * FROM course WHERE course_id = #{courseId} FOR UPDATE") CourseRecord selectForUpdate(String courseId); }
