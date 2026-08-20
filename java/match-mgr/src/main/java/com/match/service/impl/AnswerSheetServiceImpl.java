package com.match.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.match.entity.AnswerSheet;
import com.match.entity.Subject;
import com.match.entity.User;
import com.match.mapper.AnswerSheetMapper;
import com.match.mapper.TestPaperMapper;
import com.match.service.AnswerSheetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnswerSheetServiceImpl implements AnswerSheetService {


    @Autowired
    private AnswerSheetMapper answerSheetMapper;

    @Autowired
    private TestPaperMapper testPaperMapper;



    @Override
    public int addAnswer(AnswerSheet answerSheet) {
        int insert = answerSheetMapper.insert(answerSheet);
        return insert;
    }

    @Override
    public JSONArray selectAnswer(User user,Subject sub) {

        QueryWrapper<AnswerSheet> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id",user.getUserId());
        List<AnswerSheet> answerSheets = answerSheetMapper.selectList(wrapper);
        JSONArray jsonArray = new JSONArray();
        for (AnswerSheet answerSheet:
                answerSheets) {
            Subject subject = testPaperMapper.selectById(answerSheet.getSubjectId());
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("subject",subject);
            jsonObject.put("answerSheet",answerSheet);
            jsonArray.add(jsonObject);
        }


        return jsonArray;
    }

    @Override
    public AnswerSheet getAnswerSheetOne(AnswerSheet answerSheet1) {
        QueryWrapper<AnswerSheet> wrapper = new QueryWrapper<>();
        wrapper.eq("subject_id",answerSheet1.getSubjectId());
        wrapper.eq("user_id",answerSheet1.getUserId());

        return answerSheetMapper.selectOne(wrapper);
    }

    @Override
    public int updataAnswerSheet(AnswerSheet answerSheet) {
        QueryWrapper<AnswerSheet> wrapper = new QueryWrapper<>();
        wrapper.eq("subject_id",answerSheet.getSubjectId());
        wrapper.eq("user_id",answerSheet.getUserId());
        int update = answerSheetMapper.update(answerSheet, wrapper);

        return update;
    }
}
