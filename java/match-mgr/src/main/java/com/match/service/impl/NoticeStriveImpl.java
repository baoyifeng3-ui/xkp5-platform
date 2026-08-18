package com.match.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.match.entity.Notice;
import com.match.mapper.NoticeMapper;
import com.match.service.NoticeStrive;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.List;

@Service
public class NoticeStriveImpl implements NoticeStrive {
    private final NoticeMapper noticeMapper;

    public NoticeStriveImpl(NoticeMapper noticeMapper) {
        this.noticeMapper = noticeMapper;
    }

    @Override
    public List<Notice> getNotices() {
        Notice notice = getNotice();
        return notice == null ? Collections.emptyList() : Collections.singletonList(notice);
    }

    @Override
    public Notice getNotice() {
        List<Notice> notices = noticeMapper.selectList(new QueryWrapper<Notice>()
                .orderByAsc("notice_id").last("LIMIT 1"));
        return notices.isEmpty() ? null : notices.get(0);
    }

    @Override
    @Transactional
    public Notice saveNotice(String content) {
        Notice notice = getNotice();
        if (notice == null) {
            notice = new Notice();
            notice.setCreationTime(new Date());
            notice.setNoticeContent(content == null ? "" : content);
            noticeMapper.insert(notice);
        } else {
            notice.setNoticeContent(content == null ? "" : content);
            noticeMapper.updateById(notice);
        }
        return notice;
    }
}
