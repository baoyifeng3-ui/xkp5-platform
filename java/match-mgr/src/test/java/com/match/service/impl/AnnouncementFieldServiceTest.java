package com.match.service.impl;

import com.match.entity.AnnouncementField;
import com.match.entity.AnnouncementFieldValue;
import com.match.mapper.AnnouncementFieldMapper;
import com.match.mapper.AnnouncementFieldValueMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AnnouncementFieldServiceTest {
    @Test
    public void savesPreviouslyUnknownFieldName() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace("com.match.test.fields");
        TableInfoHelper.initTableInfo(assistant, AnnouncementField.class);
        TableInfoHelper.initTableInfo(assistant, AnnouncementFieldValue.class);
        AnnouncementFieldMapper fields = mock(AnnouncementFieldMapper.class);
        AnnouncementFieldValueMapper values = mock(AnnouncementFieldValueMapper.class);
        when(fields.selectList(any())).thenReturn(Collections.emptyList());
        when(fields.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            ((AnnouncementField) invocation.getArgument(0)).setFieldId(9);
            return 1;
        }).when(fields).insert(any(AnnouncementField.class));
        AnnouncementFieldService service = new AnnouncementFieldService(fields, values);

        service.saveCustomValues(3, Collections.singletonMap("班级", "一班"), 1);

        ArgumentCaptor<AnnouncementField> field = ArgumentCaptor.forClass(AnnouncementField.class);
        verify(fields).insert(field.capture());
        assertEquals("班级", field.getValue().getFieldName());
        ArgumentCaptor<AnnouncementFieldValue> value = ArgumentCaptor.forClass(AnnouncementFieldValue.class);
        verify(values).insert(value.capture());
        assertEquals(Integer.valueOf(9), value.getValue().getFieldId());
        assertEquals("一班", value.getValue().getFieldValue());
    }
}
