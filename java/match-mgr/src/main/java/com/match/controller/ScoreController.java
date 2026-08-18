package com.match.controller;


import com.alibaba.fastjson.JSONArray;
import com.match.entity.Score;
import com.match.service.TeamsService;
import com.match.service.impl.ScoreServiceImpl;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author zhaoyu
 * @since 2022-03-22
 */
@RestController
@RequestMapping("/score")
//@ResponseStatus
@Api

public class ScoreController {

    @Autowired
    ScoreServiceImpl scoreService;

    @Autowired
    com.match.service.impl.SystemSettingService systemSettingService;

    @Autowired
    com.match.service.impl.PaperResourceService paperResourceService;


    @Autowired
    TeamsService teamsService;

    @PutMapping()
    public ResponseResult<Object> save(String anotation, String paperType) {
        try {
            String activePaper = systemSettingService.getActivePaper();
            if (activePaper.isEmpty()) {
                return Response.makeRsp(400, "管理员尚未选择当前赛卷");
            }
            if (paperType != null && !activePaper.equalsIgnoreCase(paperType)) {
                return Response.makeRsp(400, "当前只能提交管理员选择的赛卷");
            }
            paperResourceService.requireReady(activePaper);

            Score score = scoreService.excPy(anotation, activePaper);
            //将 检测值保存在成绩表中
            scoreService.saveScore(score.getScore());

            return Response.makeOKRsp(score);
        } catch (Exception e) {
            e.printStackTrace();
            return Response.makeErrRsp(e);
        }
    }

    @GetMapping("top")
    public ResponseResult<Object> top(){
        try {
            //将成绩表  按 倒序  根据用户名分组  排序
            List<Score> scores = scoreService.list().parallelStream()
                    .collect(Collectors.toMap(Score::getUserId, Function.identity(), (c1, c2) -> c1.getCreateTime().getTime() > c2.getCreateTime().getTime() ? c1 : c2))
                    .values()
                    .stream().sorted(Comparator.comparing(Score::getScore).reversed())
                    .collect(Collectors.toList());

            scoreService.relUser(scores);


            return Response.makeOKRsp(scores);

        } catch (Exception e) {
            e.printStackTrace();
            return Response.makeErrRsp(e);
        }
    }

}
