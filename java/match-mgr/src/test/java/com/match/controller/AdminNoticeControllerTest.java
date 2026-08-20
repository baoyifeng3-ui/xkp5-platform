package com.match.controller;

import com.match.dto.NoticeRequest;
import com.match.entity.Notice;
import com.match.security.AdminAccessException;
import com.match.security.AdminGuard;
import com.match.service.NoticeStrive;
import com.match.util.result.ResponseResult;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AdminNoticeControllerTest {
    @Mock private NoticeStrive noticeService;
    @Mock private AdminGuard adminGuard;
    private AdminNoticeController controller;

    @Before
    public void setUp() {
        controller = new AdminNoticeController(noticeService, adminGuard);
    }

    @Test
    public void savesNoticeAfterAdminCheck() {
        NoticeRequest request = new NoticeRequest();
        request.setNoticeContent("第一行\n第二行");
        Notice saved = new Notice();
        saved.setNoticeContent(request.getNoticeContent());
        when(noticeService.saveNotice(request.getNoticeContent())).thenReturn(saved);

        ResponseResult<Object> result = controller.save(request);

        assertEquals(200, result.getCode());
        assertEquals(request.getNoticeContent(), ((Map<?, ?>) result.getData()).get("noticeContent"));
        verify(adminGuard).requireAdmin();
    }

    @Test
    public void rejectsSaveWhenAdminCheckFails() {
        NoticeRequest request = new NoticeRequest();
        request.setNoticeContent("内容");
        doThrow(new AdminAccessException("仅管理员可以执行此操作"))
                .when(adminGuard).requireAdmin();

        try {
            controller.save(request);
            fail("非管理员不应保存注意事项");
        } catch (AdminAccessException exception) {
            assertEquals("仅管理员可以执行此操作", exception.getMessage());
        }

        verify(noticeService, never()).saveNotice(request.getNoticeContent());
    }
}
