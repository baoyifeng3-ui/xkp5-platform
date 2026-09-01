package com.match.help.persistence;
import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.LocalDateTime;
@Data @TableName("help_document") public class HelpDocumentRecord{@TableId(value="document_id",type=IdType.INPUT)private String documentId;private String title;private String audience;private String contentType;private String htmlContent;private String storageKey;private String fileName;private Boolean published;private Integer createdBy;private LocalDateTime createdAt;private LocalDateTime updatedAt;}
