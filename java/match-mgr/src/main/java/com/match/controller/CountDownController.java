package com.match.controller;

import com.match.dto.CountDownResponse;
import com.match.service.impl.CountDownServiceImpl;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/countdown")
public class CountDownController {
    private final CountDownServiceImpl countDownService;

    public CountDownController(CountDownServiceImpl countDownService) {
        this.countDownService = countDownService;
    }

    @GetMapping
    public ResponseResult<CountDownResponse> countDown() {
        return Response.makeOKRsp(countDownService.snapshot());
    }
}
