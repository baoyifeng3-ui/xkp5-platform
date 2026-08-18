package com.match.service;

import com.alibaba.fastjson.JSONArray;
import com.match.entity.AnswerSheet;
import com.match.entity.Subject;
import com.match.entity.User;

public interface AnswerSheetService {

    int addAnswer(AnswerSheet answerSheet);

    JSONArray selectAnswer(User user, Subject subject);

    AnswerSheet getAnswerSheetOne(AnswerSheet answerSheet1);

    int updataAnswerSheet(AnswerSheet answerSheet);
}
