package com.match.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.match.entity.Subject;
import com.match.entity.User;
import com.match.mapper.AnswerSheetMapper;
import com.match.mapper.TestPaperMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TestPaperServiceImplTest {
    @Mock private TestPaperMapper subjectMapper;
    @Mock private AnswerSheetMapper answerSheetMapper;
    @Mock private TrainingEnvironmentService trainingEnvironmentService;
    @Mock private SubjectManagementService subjectManagementService;

    private TestPaperServiceImpl service;

    @Before
    public void setUp() {
        service = new TestPaperServiceImpl();
        ReflectionTestUtils.setField(service, "testPaperMapper", subjectMapper);
        ReflectionTestUtils.setField(service, "answerSheetMapper", answerSheetMapper);
        ReflectionTestUtils.setField(service, "trainingEnvironmentService", trainingEnvironmentService);
        ReflectionTestUtils.setField(service, "subjectManagementService", subjectManagementService);
    }

    @Test
    public void removesCorrectAnswerFromParticipantPaperResponse() {
        Subject subject = new Subject();
        subject.setSubjectId(1);
        subject.setTestPaperType("A");
        subject.setSubjectType("single_choice");
        subject.setCorrectAnswer("A");
        when(subjectMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(subject));
        when(answerSheetMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        User user = new User();
        user.setUserId(7);
        Subject filter = new Subject();
        filter.setTestPaperType("A");

        JSONArray result = service.getSubject(user, filter);

        assertNull(result.getJSONObject(0).getJSONObject("subject").get("correctAnswer"));
    }
}
