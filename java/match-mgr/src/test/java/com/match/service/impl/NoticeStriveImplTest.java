package com.match.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.match.entity.Notice;
import com.match.mapper.NoticeMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class NoticeStriveImplTest {
    @Mock private NoticeMapper noticeMapper;
    private NoticeStriveImpl service;

    @Before
    public void setUp() {
        service = new NoticeStriveImpl(noticeMapper);
    }

    @Test
    public void createsNoticeWhenNoneExists() {
        when(noticeMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        Notice saved = service.saveNotice("第一行\n第二行");

        assertEquals("第一行\n第二行", saved.getNoticeContent());
        verify(noticeMapper).insert(saved);
    }

    @Test
    public void updatesExistingNotice() {
        Notice existing = new Notice();
        existing.setNoticeId(1);
        existing.setNoticeContent("旧内容");
        when(noticeMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(existing));

        Notice saved = service.saveNotice("新内容");

        assertEquals("新内容", saved.getNoticeContent());
        verify(noticeMapper).updateById(existing);
    }
}
