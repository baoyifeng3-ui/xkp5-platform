package com.match.course.persistence;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
public interface CourseDeliveryMapper extends BaseMapper<CourseDeliveryRecord> {
    @Select("SELECT * FROM course_resource_delivery WHERE resource_id=#{resourceId} AND user_id=#{userId} AND environment_id=#{environmentId} AND digest=(SELECT sha256 FROM course_resource WHERE resource_id=#{resourceId}) FOR UPDATE")
    CourseDeliveryRecord selectDeliveryForUpdate(@Param("resourceId") String resourceId, @Param("userId") Integer userId, @Param("environmentId") String environmentId);
    @Select("SELECT * FROM course_resource_delivery WHERE command_id=#{commandId} FOR UPDATE")
    CourseDeliveryRecord selectByCommandId(@Param("commandId") String commandId);
}
