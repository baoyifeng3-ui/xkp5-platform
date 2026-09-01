package com.match.learning.persistence;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("learning_evaluation") public class LearningEvaluationRecord {
 @TableId(value="evaluation_id",type=IdType.INPUT) private String evaluationId; private Integer userId; private String courseId; private Integer rating; private String content; private Integer createdBy; private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
