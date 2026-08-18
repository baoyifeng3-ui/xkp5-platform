package com.match.service.impl;

import com.match.entity.AnnouncementField;
import com.match.mapper.AnnouncementFieldMapper;
import com.match.mapper.AnnouncementFieldValueMapper;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AnnouncementFieldServiceTest {
    @Mock private AnnouncementFieldMapper fieldMapper;
    @Mock private AnnouncementFieldValueMapper valueMapper;

    @Before
    public void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace("com.match.announcement.test");
        TableInfoHelper.initTableInfo(assistant, AnnouncementField.class);
    }

    @Test
    public void createsEnabledCustomFieldAfterCurrentOrder() {
        AnnouncementField last = new AnnouncementField();
        last.setSortOrder(40);
        when(fieldMapper.selectOne(any())).thenReturn(last);
        doAnswer(invocation -> {
            AnnouncementField field = invocation.getArgument(0);
            field.setFieldId(8);
            return 1;
        }).when(fieldMapper).insert(any(AnnouncementField.class));
        AnnouncementFieldService service = new AnnouncementFieldService(fieldMapper, valueMapper);

        AnnouncementField created = service.create(" 赛位号 ", 1);

        assertEquals("赛位号", created.getFieldName());
        assertEquals("CUSTOM", created.getFieldType());
        assertTrue(created.getEnabled());
        assertEquals(Integer.valueOf(50), created.getSortOrder());
    }

    @Test
    public void reportsDuplicateName() {
        when(fieldMapper.selectOne(any())).thenReturn(null);
        when(fieldMapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));
        AnnouncementFieldService service = new AnnouncementFieldService(fieldMapper, valueMapper);

        try {
            service.create("学校名称", 1);
            fail("重复字段名应被拒绝");
        } catch (IllegalArgumentException exception) {
            assertTrue(exception.getMessage().contains("已存在"));
        }
    }
}
