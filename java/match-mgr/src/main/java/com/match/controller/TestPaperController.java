package com.match.controller;

import com.alibaba.fastjson.JSONArray;
import com.match.entity.*;
import com.match.service.AnswerSheetService;
import com.match.service.TeamsService;
import com.match.service.TestPaperService;
import com.match.util.dfs.FastDFSClient;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import com.match.service.impl.SystemSettingService;
import com.match.service.impl.SubjectManagementService;
import com.match.service.impl.AnswerImageReferenceService;
import com.match.service.impl.PaperGradingService;
import com.match.security.ParticipantAccessGuard;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * <p>
 *  试卷
 * </p>
 *
 * @author jy
 * @since 2022-09-21
 */
@RestController
@RequestMapping("/testPaper")
@Api

public class TestPaperController {
    @Autowired
    TestPaperService testPaperService;

    @Autowired
    AnswerSheetService answerSheetService;


    @Autowired
    TeamsService teamsService;

    @Autowired
    SystemSettingService systemSettingService;

    @Autowired
    SubjectManagementService subjectManagementService;

    @Autowired
    AnswerImageReferenceService answerImageReferenceService;

    @Autowired
    PaperGradingService paperGradingService;
    @Autowired
    ParticipantAccessGuard participantAccessGuard;



  /*  @GetMapping("subject") h 获取题目接口 已废弃
    public ResponseResult<Object> TestPaper(Subject subject) {

        try {
            if (subject.getTestPaperType()!=null){
                List<Subject> subjectList = testPaperService.getSubject(subject);
                return Response.makeOKRsp(subjectList);
            }else {
                return Response.makeErrRsp("请传入试卷类型");
            }


        } catch (Exception e) {
            e.printStackTrace();
            return Response.makeErrRsp(e);
        }

    }*/


    @PostMapping("addAnswer")
    public ResponseResult<Object> addAnswer(AnswerSheet answerSheet, MultipartFile[] files,
                                            String[] retainedImages) {
        participantAccessGuard.requireCompetitionStarted();
        participantAccessGuard.requireParticipant();
        answerSheet.setUserId(StpUtil.getLoginIdAsInt());
        Subject subject = subjectManagementService.requireSubject(answerSheet.getSubjectId());
        String activePaper = systemSettingService.getActivePaper();
        if (activePaper.isEmpty() || !activePaper.equalsIgnoreCase(subject.getTestPaperType())) {
            throw new IllegalArgumentException("当前题目不属于管理员启用的赛卷");
        }
        paperGradingService.requireEditable(answerSheet.getUserId(), subject.getTestPaperType());

        answerSheet.setSubjectType(subject.getSubjectType());
        answerSheet.setCreationTime(new Date());
        AnswerSheet existing = answerSheetService.getAnswerSheetOne(answerSheet);
        if (SubjectType.PRACTICAL.getCode().equals(subject.getSubjectType())) {
            answerSheet.setAnswerText("");
            List<String> retained = answerImageReferenceService.validateRetainedImages(existing, retainedImages);
            answerSheet.setAnswerImg(answerImageReferenceService.combine(retained, uploadFiles(files)));
        } else {
            answerSheet.setAnswerText(subjectManagementService.validateAnswer(subject, answerSheet.getAnswerText()));
            answerSheet.setAnswerImg(null);
        }

        if (existing != null) {
            answerSheet.setAnswerId(existing.getAnswerId());
            answerSheetService.updataAnswerSheet(answerSheet);
            return savedResponse(answerSheet, "修改成功！");
        }

        if (answerSheetService.addAnswer(answerSheet) <= 0) {
            return Response.makeErrRsp("保存失败！");
        }
        increaseTeamProgress(answerSheet.getUserId());
        return savedResponse(answerSheet, "保存成功！");
    }

    @GetMapping("submission")
    public ResponseResult<Object> submissionStatus(String testPaperType) {
        return Response.makeOKRsp(paperGradingService.participantStatus(
                StpUtil.getLoginIdAsInt(), testPaperType));
    }

    @PostMapping("submit")
    public ResponseResult<Object> submit(String testPaperType) {
        participantAccessGuard.requireCompetitionStarted();
        participantAccessGuard.requireParticipant();
        String activePaper = systemSettingService.getActivePaper();
        if (activePaper.isEmpty() || testPaperType == null
                || !activePaper.equalsIgnoreCase(testPaperType)) {
            throw new IllegalArgumentException("当前只能提交管理员启用的赛卷");
        }
        return Response.makeOKRsp(paperGradingService.submit(
                StpUtil.getLoginIdAsInt(), activePaper));
    }

    private ResponseResult<Object> savedResponse(AnswerSheet answerSheet, String message) {
        if (SubjectType.PRACTICAL.getCode().equals(answerSheet.getSubjectType())) {
            return Response.makeRsp(200, message, answerSheet.getAnswerImg());
        }
        return Response.makeOKRsp(message);
    }

    private List<String> uploadFiles(MultipartFile[] files) {
        List<String> urls = new ArrayList<>();
        if (files == null) {
            return urls;
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String path = FastDFSClient.uploadFile(file);
            if (path == null || path.trim().isEmpty()) {
                throw new IllegalStateException("图片上传失败，请稍后重试");
            }
            urls.add(FastDFSClient.getResAccessUrl(path));
        }
        return urls;
    }

    private void increaseTeamProgress(Integer userId) {
        TeamsUser teamsUser = teamsService.selectOne(userId);
        if (teamsUser == null) {
            return;
        }
        Teams teams = teamsService.getTeamsOne(teamsUser.getTeamsId());
        if (teams == null) {
            return;
        }
        teams.setSpeedProgress((teams.getSpeedProgress() == null ? 0 : teams.getSpeedProgress()) + 1);
        teamsService.setTeamsOne(teams);
    }

    @GetMapping("getAnswer")
    public ResponseResult<Object> getAnswer(User user,Subject subject) {
            participantAccessGuard.requireCompetitionStarted();
            user.setUserId(StpUtil.getLoginIdAsInt());
            String activePaper = systemSettingService.getActivePaper();
            if (activePaper.isEmpty()) {
                return Response.makeErrRsp("管理员尚未选择当前赛卷");
            }
            if (subject.getTestPaperType() == null || !activePaper.equalsIgnoreCase(subject.getTestPaperType())) {
                return Response.makeErrRsp("当前只能进入管理员选择的赛卷");
            }
        try {


            JSONArray jsonArray = testPaperService.getSubject(user,subject);

            if (jsonArray.size()==0){
                return Response.makeErrRsp("当前试卷暂无题目，请联系管理员");
            }else{
                return Response.makeOKRsp(jsonArray);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Response.makeErrRsp(e);
        }

    }

}
