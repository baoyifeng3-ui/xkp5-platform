package com.match.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.entity.AnnouncementField;
import com.match.entity.CompetitionAnnouncementEntry;
import com.match.mapper.CompetitionAnnouncementEntryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CompetitionAnnouncementService {
    private final CompetitionAnnouncementEntryMapper mapper;
    private final AnnouncementFieldService fields;
    private final ObjectMapper json;

    public CompetitionAnnouncementService(CompetitionAnnouncementEntryMapper mapper,
                                          AnnouncementFieldService fields, ObjectMapper json) {
        this.mapper = mapper; this.fields = fields; this.json = json;
    }

    public List<Map<String, Object>> list() {
        List<CompetitionAnnouncementEntry> rows = mapper.selectList(
                Wrappers.<CompetitionAnnouncementEntry>lambdaQuery()
                        .orderByAsc(CompetitionAnnouncementEntry::getSortOrder)
                        .orderByAsc(CompetitionAnnouncementEntry::getEntryId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) result.add(view(rows.get(index), index + 1));
        return result;
    }

    @Transactional
    public Map<String, Object> create(Map<String, String> requested) {
        Map<String, String> values = validate(requested);
        CompetitionAnnouncementEntry entry = new CompetitionAnnouncementEntry();
        entry.setValuesJson(write(values));
        entry.setSortOrder(mapper.selectCount(Wrappers.emptyWrapper()) + 1);
        entry.setCreatedAt(new Date()); entry.setUpdatedAt(entry.getCreatedAt());
        mapper.insert(entry);
        return view(entry, entry.getSortOrder());
    }

    @Transactional
    public Map<String, Object> update(Integer id, Map<String, String> requested) {
        CompetitionAnnouncementEntry entry = mapper.selectById(id);
        if (entry == null) throw new IllegalArgumentException("公告条目不存在");
        entry.setValuesJson(write(validate(requested))); entry.setUpdatedAt(new Date());
        mapper.updateById(entry);
        return view(entry, entry.getSortOrder());
    }

    @Transactional
    public void delete(Integer id) {
        if (mapper.deleteById(id) != 1) throw new IllegalArgumentException("公告条目不存在");
    }

    private Map<String, String> validate(Map<String, String> requested) {
        Map<String, String> values = requested == null ? Collections.emptyMap() : requested;
        Set<String> allowed = fields.enabledFields().stream().map(AnnouncementField::getFieldKey)
                .filter(key -> !"sequence".equals(key)).collect(Collectors.toSet());
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> item : values.entrySet()) {
            if ("sequence".equals(item.getKey())) continue;
            if (!allowed.contains(item.getKey()))
                throw new IllegalArgumentException("公告包含未启用字段: " + item.getKey());
            String value = item.getValue() == null ? "" : item.getValue().trim();
            if (value.length() > 255) throw new IllegalArgumentException("公告字段内容不能超过 255 个字符");
            normalized.put(item.getKey(), value);
        }
        return normalized;
    }

    private Map<String, Object> view(CompetitionAnnouncementEntry entry, int sequence) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entryId", entry.getEntryId()); result.put("sequence", sequence);
        result.putAll(read(entry.getValuesJson()));
        return result;
    }

    private String write(Map<String, String> values) {
        try { return json.writeValueAsString(values); }
        catch (Exception error) { throw new IllegalStateException("公告内容无法保存", error); }
    }

    private Map<String, String> read(String value) {
        try { return json.readValue(value, new TypeReference<Map<String, String>>() { }); }
        catch (Exception error) { throw new IllegalStateException("公告内容无法读取", error); }
    }
}
