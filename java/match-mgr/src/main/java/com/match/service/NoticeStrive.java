package com.match.service;

import com.match.entity.Notice;

import java.util.List;

public interface NoticeStrive {
    List<Notice> getNotices();

    Notice getNotice();

    Notice saveNotice(String content);
}
