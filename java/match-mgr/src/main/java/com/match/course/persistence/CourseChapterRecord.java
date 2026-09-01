package com.match.course.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("course_chapter")
public class CourseChapterRecord {
    @TableId(value="chapter_id", type=IdType.INPUT) private String chapterId;
    private String courseId; private String chapterName; private String practiceTool;
    private Integer sortOrder; private Integer createdBy; private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
