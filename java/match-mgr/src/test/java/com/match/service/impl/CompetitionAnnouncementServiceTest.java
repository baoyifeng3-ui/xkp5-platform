package com.match.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.entity.AnnouncementField;
import com.match.entity.CompetitionAnnouncementEntry;
import com.match.mapper.CompetitionAnnouncementEntryMapper;
import org.junit.Test;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CompetitionAnnouncementServiceTest {
    @Test
    public void createStoresOnlyEnabledIndependentFields() {
        CompetitionAnnouncementEntryMapper mapper = mock(CompetitionAnnouncementEntryMapper.class);
        AnnouncementFieldService fields = mock(AnnouncementFieldService.class);
        when(fields.enabledFields()).thenReturn(Collections.singletonList(field("schoolName")));
        when(mapper.selectCount(any())).thenReturn(2);
        Map<String, String> values = new LinkedHashMap<>(); values.put("schoolName", "示例学校");

        Map<String, Object> result = new CompetitionAnnouncementService(mapper, fields, new ObjectMapper())
                .create(values);

        assertEquals(3, result.get("sequence"));
        assertEquals("示例学校", result.get("schoolName"));
        verify(mapper).insert(any(CompetitionAnnouncementEntry.class));
    }

    @Test
    public void rejectsUnknownFields() {
        CompetitionAnnouncementEntryMapper mapper = mock(CompetitionAnnouncementEntryMapper.class);
        AnnouncementFieldService fields = mock(AnnouncementFieldService.class);
        when(fields.enabledFields()).thenReturn(Collections.singletonList(field("schoolName")));
        try {
            new CompetitionAnnouncementService(mapper, fields, new ObjectMapper())
                    .create(Collections.singletonMap("userId", "3"));
            fail("unknown field must be rejected");
        } catch (IllegalArgumentException expected) {
            assertEquals("公告包含未启用字段: userId", expected.getMessage());
        }
    }

    private AnnouncementField field(String key) {
        AnnouncementField field = new AnnouncementField();
        field.setFieldKey(key); field.setFieldName(key); field.setEnabled(true);
        return field;
    }
}
