package com.match.course.persistence;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("course_training_report") public class CourseTrainingReportRecord { @TableId(value="report_id",type=IdType.INPUT) private String reportId; private Integer userId; private String courseId; private String content; private LocalDateTime updatedAt; }
