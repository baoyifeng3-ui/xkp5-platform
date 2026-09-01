package com.match.learning.persistence;
import org.apache.ibatis.annotations.*; import java.util.*;
public interface LearningAnalysisMapper {
 @Select("SELECT u.user_id userId,u.user_name userName,COUNT(DISTINCT p.resource_id) resourcesStarted,COALESCE(AVG(p.percent),0) averageProgress,SUM(p.state='COMPLETED') completedResources FROM user u LEFT JOIN course_resource_progress p ON p.user_id=u.user_id WHERE u.enabled=1 GROUP BY u.user_id,u.user_name ORDER BY averageProgress DESC") List<Map<String,Object>> userSummary();
 @Select("SELECT COUNT(*) totalUsers,COUNT(DISTINCT p.user_id) activeUsers,COALESCE(AVG(p.percent),0) averageProgress,SUM(p.state='COMPLETED') completedResources FROM user u LEFT JOIN course_resource_progress p ON p.user_id=u.user_id WHERE u.enabled=1") Map<String,Object> overview();
 @Select("SELECT * FROM learning_evaluation WHERE user_id=#{userId} ORDER BY updated_at DESC") List<LearningEvaluationRecord> evaluations(@Param("userId") Integer userId);
}
