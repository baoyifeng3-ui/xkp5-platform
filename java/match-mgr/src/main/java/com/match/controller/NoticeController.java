package com.match.controller;

import com.match.entity.Notice;
import com.match.service.NoticeStrive;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 *  公告
 * </p>
 *
 * @author jy
 * @since 2022-09-26
 */
@RestController
@RequestMapping("/notice")
@Api
public class NoticeController {

    @Autowired
    NoticeStrive noticeStrive;

    @GetMapping("getNotice")
    public ResponseResult<Object> getNotice( ) {

        try {


           List<Notice> notices =  noticeStrive.getNotices();

            return Response.makeOKRsp(notices);

        } catch (Exception e) {
            e.printStackTrace();
            return Response.makeErrRsp(e);
        }

    }
}
