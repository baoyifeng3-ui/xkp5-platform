package com.match.course.persistence;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
public interface CourseResourceMapper extends BaseMapper<CourseResourceRecord> { @Select("SELECT * FROM course_resource WHERE enabled = 1 AND course_id IN (SELECT course_id FROM course WHERE enabled = 1) ORDER BY course_id, sort_order") List<CourseResourceRecord> selectVisible(Integer userId); }
