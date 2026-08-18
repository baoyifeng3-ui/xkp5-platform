package com.match.controller;

import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.match.mapper.TeamsUserMapper;
import com.match.mapper.UserMapper;
import com.match.service.TeamsService;
import com.match.service.impl.TeamsServiceImpl;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 *  队伍
 * </p>
 *
 * @author jy
 * @since 2022-09-22
 */
@RestController
@RequestMapping("/teams")
@Api

public class TeamsController {

    @Autowired
    TeamsService teamsService;
    @Autowired
    TeamsUserMapper teamsUserMapper;
    @Autowired
    UserMapper userMapper;
    @Autowired
    TeamsServiceImpl teamsServiceImpl;


    @GetMapping()
    public ResponseResult<Object> Teams( ) {

        try {
            return Response.makeOKRsp(teamsServiceImpl.getHomepageData());

        } catch (Exception e) {
            e.printStackTrace();
            return Response.makeErrRsp(e);
        }

    }

    @GetMapping("teamsUser")
    public ResponseResult<Object> teamsUser( ) {

        try {
            return Response.makeOKRsp(teamsUserMapper.selectList(Wrappers.emptyWrapper()));
        } catch (Exception e) {
            e.printStackTrace();
            return Response.makeErrRsp(e);
        }
    }

    @GetMapping("user")
    public ResponseResult<Object> user( ) {

        try {
            java.util.List<com.match.entity.User> users = userMapper.selectList(Wrappers.emptyWrapper());
            users.forEach(user -> user.setPassword(null));
            return Response.makeOKRsp(users);
        } catch (Exception e) {
            e.printStackTrace();
            return Response.makeErrRsp(e);
        }
    }



}
