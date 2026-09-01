package com.match.course.persistence;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import org.apache.ibatis.annotations.*;
public interface CourseTrainingReportMapper extends BaseMapper<CourseTrainingReportRecord> { @Select("SELECT * FROM course_training_report WHERE user_id=#{userId} AND course_id=#{courseId} LIMIT 1") CourseTrainingReportRecord selectUserCourse(@Param("userId") Integer userId,@Param("courseId") String courseId); }
