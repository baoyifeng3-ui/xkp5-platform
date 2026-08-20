package com.match.controller;


import com.match.entity.TrainUrl;
import com.match.service.impl.TrainUrlServiceImpl;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author zhaoyu
 * @since 2022-03-22
 */
@RestController
@RequestMapping("/train-url")
//@ResponseStatus
@Api
public class TrainUrlController {

    @Autowired
    TrainUrlServiceImpl trainUrlService;

    @GetMapping()
    public ResponseResult<Object> trainUrl( ) {

        try {
            TrainUrl trainUrl = trainUrlService.getByUser();
            return Response.makeOKRsp(trainUrl);

        } catch (Exception e) {
            e.printStackTrace();
            return Response.makeErrRsp(e);
        }

    }

}

