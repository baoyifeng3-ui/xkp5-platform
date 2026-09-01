package com.match.help.service;

import com.match.help.persistence.HelpDocumentMapper;
import com.match.help.persistence.HelpDocumentRecord;
import com.match.resource.service.ResourceStorageService;
import org.apache.commons.io.FilenameUtils;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class HelpDocumentService {
    private final HelpDocumentMapper mapper;
    private final ResourceStorageService storage;

    public HelpDocumentService(HelpDocumentMapper mapper, ResourceStorageService storage) {
        this.mapper = mapper;
        this.storage = storage;
    }

    public List<HelpDocumentRecord> all() { return mapper.selectAllOrdered(); }
    public List<HelpDocumentRecord> visible(boolean admin) {
        return mapper.selectPublished(admin ? "'USER','ADMIN'" : "'USER'");
    }

    public HelpDocumentRecord save(Map<String, Object> body, int actor) {
        String id = text(body, "documentId"), audience = text(body, "audience");
        validateAudience(audience);
        HelpDocumentRecord record = id.isEmpty() ? new HelpDocumentRecord() : mapper.selectById(id);
        if (record == null) throw new IllegalArgumentException("帮助文档不存在");
        if (!id.isEmpty() && !"HTML".equals(record.getContentType())) {
            throw new IllegalArgumentException("上传文档只能修改发布状态");
        }
        LocalDateTime now = LocalDateTime.now();
        if (id.isEmpty()) {
            record.setDocumentId(UUID.randomUUID().toString());
            record.setCreatedBy(actor); record.setCreatedAt(now); record.setContentType("HTML");
        }
        record.setTitle(text(body, "title"));
        if (record.getTitle().isEmpty()) throw new IllegalArgumentException("文档标题不能为空");
        record.setAudience(audience);
        Safelist safe = Safelist.relaxed().addTags("table", "thead", "tbody", "tr", "th", "td")
                .addAttributes(":all", "style");
        record.setHtmlContent(Jsoup.clean(text(body, "htmlContent"), safe));
        record.setPublished(Boolean.TRUE.equals(body.get("published"))); record.setUpdatedAt(now);
        if (id.isEmpty()) mapper.insert(record); else mapper.updateById(record);
        return record;
    }

    public HelpDocumentRecord upload(String title, String audience, MultipartFile file, int actor) {
        validateAudience(audience);
        if (title == null || title.trim().isEmpty()) throw new IllegalArgumentException("文档标题不能为空");
        String extension = FilenameUtils.getExtension(file.getOriginalFilename()).toLowerCase();
        String type = "pdf".equals(extension) ? "PDF" : Arrays.asList("doc", "docx").contains(extension) ? "WORD" : null;
        if (type == null) throw new IllegalArgumentException("仅支持 PDF、DOC、DOCX");
        ResourceStorageService.StoredFile stored = storage.store(file);
        HelpDocumentRecord record = new HelpDocumentRecord();
        record.setDocumentId(UUID.randomUUID().toString()); record.setTitle(title.trim());
        record.setAudience(audience); record.setContentType(type); record.setStorageKey(stored.getStorageKey());
        record.setFileName(file.getOriginalFilename()); record.setPublished(false); record.setCreatedBy(actor);
        record.setCreatedAt(LocalDateTime.now()); record.setUpdatedAt(record.getCreatedAt()); mapper.insert(record);
        return record;
    }

    public HelpDocumentRecord publish(String id, boolean value) {
        HelpDocumentRecord record = mapper.selectById(id);
        if (record == null) throw new IllegalArgumentException("帮助文档不存在");
        record.setPublished(value); record.setUpdatedAt(LocalDateTime.now()); mapper.updateById(record);
        return record;
    }

    private void validateAudience(String value) {
        if (!Arrays.asList("USER", "ADMIN").contains(value)) throw new IllegalArgumentException("帮助文档受众无效");
    }
    private String text(Map<String, Object> body, String key) {
        return body == null || body.get(key) == null ? "" : String.valueOf(body.get(key)).trim();
    }
}
