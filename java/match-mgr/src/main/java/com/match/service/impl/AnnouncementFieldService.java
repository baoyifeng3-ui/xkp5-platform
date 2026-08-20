package com.match.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.match.entity.AnnouncementField;
import com.match.entity.AnnouncementFieldValue;
import com.match.mapper.AnnouncementFieldMapper;
import com.match.mapper.AnnouncementFieldValueMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AnnouncementFieldService {
    public static final String CUSTOM = "CUSTOM";
    private final AnnouncementFieldMapper fieldMapper;
    private final AnnouncementFieldValueMapper valueMapper;

    public AnnouncementFieldService(AnnouncementFieldMapper fieldMapper,
                                    AnnouncementFieldValueMapper valueMapper) {
        this.fieldMapper = fieldMapper;
        this.valueMapper = valueMapper;
    }

    public List<AnnouncementField> list() {
        return fieldMapper.selectList(Wrappers.<AnnouncementField>lambdaQuery()
                .orderByAsc(AnnouncementField::getSortOrder).orderByAsc(AnnouncementField::getFieldId));
    }

    public List<AnnouncementField> enabledFields() {
        return fieldMapper.selectList(Wrappers.<AnnouncementField>lambdaQuery()
                .eq(AnnouncementField::getEnabled, true)
                .orderByAsc(AnnouncementField::getSortOrder).orderByAsc(AnnouncementField::getFieldId));
    }

    @Transactional
    public AnnouncementField create(String fieldName, Integer adminId) {
        String name = normalizeName(fieldName);
        AnnouncementField field = new AnnouncementField();
        field.setFieldKey("custom_" + UUID.randomUUID().toString().replace("-", ""));
        field.setFieldName(name);
        field.setFieldType(CUSTOM);
        field.setEnabled(true);
        AnnouncementField last = fieldMapper.selectOne(Wrappers.<AnnouncementField>lambdaQuery()
                .orderByDesc(AnnouncementField::getSortOrder).last("LIMIT 1"));
        field.setSortOrder(last == null ? 10 : last.getSortOrder() + 10);
        field.setUpdatedBy(adminId);
        try {
            fieldMapper.insert(field);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("公告字段名称已存在");
        }
        return field;
    }

    @Transactional
    public AnnouncementField update(Integer fieldId, String fieldName, Boolean enabled, Integer adminId) {
        AnnouncementField field = fieldMapper.selectById(fieldId);
        if (field == null) throw new IllegalArgumentException("公告字段不存在");
        if (fieldName != null) field.setFieldName(normalizeName(fieldName));
        if (enabled != null) field.setEnabled(enabled);
        field.setUpdatedBy(adminId);
        try {
            fieldMapper.updateById(field);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("公告字段名称已存在");
        }
        return field;
    }

    public Map<Integer, Map<String, String>> valuesByUserIds(List<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) return Collections.emptyMap();
        List<AnnouncementFieldValue> values = valueMapper.selectList(
                Wrappers.<AnnouncementFieldValue>lambdaQuery().in(AnnouncementFieldValue::getUserId, userIds));
        Map<Integer, AnnouncementField> fields = new LinkedHashMap<>();
        for (AnnouncementField field : list()) fields.put(field.getFieldId(), field);
        Map<Integer, Map<String, String>> result = new LinkedHashMap<>();
        for (AnnouncementFieldValue value : values) {
            AnnouncementField field = fields.get(value.getFieldId());
            if (field == null) continue;
            result.computeIfAbsent(value.getUserId(), ignored -> new LinkedHashMap<>())
                    .put(field.getFieldKey(), value.getFieldValue());
        }
        return result;
    }

    @Transactional
    public void saveCustomValues(Integer userId, Map<String, String> requestedValues) {
        Map<String, String> values = requestedValues == null ? Collections.emptyMap() : requestedValues;
        List<AnnouncementField> fields = list();
        for (AnnouncementField field : fields) {
            if (!CUSTOM.equals(field.getFieldType())) continue;
            String value = normalizeValue(values.get(field.getFieldKey()));
            AnnouncementFieldValue existing = valueMapper.selectOne(
                    Wrappers.<AnnouncementFieldValue>lambdaQuery()
                            .eq(AnnouncementFieldValue::getFieldId, field.getFieldId())
                            .eq(AnnouncementFieldValue::getUserId, userId));
            if (existing == null) {
                existing = new AnnouncementFieldValue();
                existing.setFieldId(field.getFieldId());
                existing.setUserId(userId);
                existing.setFieldValue(value);
                valueMapper.insert(existing);
            } else {
                existing.setFieldValue(value);
                valueMapper.updateById(existing);
            }
        }
    }

    public void deleteValuesForUsers(List<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) return;
        valueMapper.delete(Wrappers.<AnnouncementFieldValue>lambdaQuery()
                .in(AnnouncementFieldValue::getUserId, userIds));
    }

    private String normalizeName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty()) throw new IllegalArgumentException("公告字段名称不能为空");
        if (name.length() > 30) throw new IllegalArgumentException("公告字段名称不能超过 30 个字符");
        return name;
    }

    private String normalizeValue(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > 255) throw new IllegalArgumentException("公告字段内容不能超过 255 个字符");
        return normalized;
    }
}
