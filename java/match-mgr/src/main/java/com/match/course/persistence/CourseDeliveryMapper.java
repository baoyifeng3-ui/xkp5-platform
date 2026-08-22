package com.match.course.persistence;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
public interface CourseDeliveryMapper extends BaseMapper<CourseDeliveryRecord> { @Select("SELECT * FROM course_resource_delivery WHERE resource_id=#{resourceId} AND user_id=#{userId} AND environment_id=#{environmentId} FOR UPDATE") CourseDeliveryRecord selectDeliveryForUpdate(String resourceId, Integer userId, String environmentId); }
