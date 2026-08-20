package com.match.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.match.entity.AnswerSheet;
import com.match.entity.Subject;
import com.match.entity.TrainUrl;
import com.match.entity.User;
import com.match.mapper.AnswerSheetMapper;
import com.match.mapper.TestPaperMapper;
import com.match.service.TestPaperService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TestPaperServiceImpl implements TestPaperService {

    @Autowired
    private TestPaperMapper testPaperMapper;
    @Autowired
    private AnswerSheetMapper answerSheetMapper;

    @Autowired
    private TrainingEnvironmentService trainingEnvironmentService;

    @Autowired
    private SubjectManagementService subjectManagementService;


    @Override
    public JSONArray  getSubject(User user,Subject subject) {

        QueryWrapper<Subject> wrapper = new QueryWrapper<>();
        wrapper.eq("test_paper_type",subject.getTestPaperType());
        wrapper.orderByAsc("modular", "sort_order", "subject_id");
        JSONArray jsonArray = new JSONArray();

        List<Subject> subjects = testPaperMapper.selectList(wrapper);
        for (Subject sub:
                subjects) {
            JSONObject jsonObject = new JSONObject();
            subjectManagementService.prepareForResponse(sub);

            // Standard answers are restricted to administrator and grading flows.
            sub.setCorrectAnswer(null);

            if (sub.getPoint() != null && !sub.getPoint().isEmpty()) {
                TrainUrl trainUrl = trainingEnvironmentService.getForUser(user.getUserId());
                if (trainUrl != null) {
                    sub.setVscodeUrl(trainUrl.getVscodeUrl());
                    sub.setCvatUrl(trainUrl.getCvatUrl());
                    sub.setT100Url(trainUrl.getT100Url());
                }

            }



            jsonObject.put("subject",sub);

            QueryWrapper<AnswerSheet> wrapper1 = new QueryWrapper<>();
            wrapper1.eq("user_id",user.getUserId());
            wrapper1.eq("subject_id",sub.getSubjectId());


            AnswerSheet answerSheet = answerSheetMapper.selectOne(wrapper1);

            jsonObject.put("answerSheet",answerSheet);
            jsonArray.add(jsonObject);
        }


        return jsonArray;



    }
}
