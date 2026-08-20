package com.match.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.match.dto.AdminSubjectRequest;
import com.match.entity.AnswerSheet;
import com.match.entity.Subject;
import com.match.mapper.AnswerSheetMapper;
import com.match.mapper.TestPaperMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SubjectManagementServiceTest {
    @Mock
    private TestPaperMapper subjectMapper;

    @Mock
    private AnswerSheetMapper answerSheetMapper;

    @Mock
    private PaperCatalogService paperCatalogService;

    private SubjectManagementService service;

    @Before
    public void setUp() {
        when(paperCatalogService.requireRegistered("A")).thenReturn("A");
        service = new SubjectManagementService(subjectMapper, answerSheetMapper, paperCatalogService);
    }

    @Test
    public void createsSingleChoiceWithNormalizedOptions() {
        AdminSubjectRequest request = request("single_choice");
        request.setOptions(Arrays.asList(" 甲 ", "乙", "甲"));
        request.setCorrectAnswer("甲");

        Subject subject = service.create(request);

        assertEquals("A", subject.getTestPaperType());
        assertEquals(Arrays.asList("甲", "乙"), subject.getOptions());
        assertEquals("[\"甲\",\"乙\"]", subject.getSubjectOptions());
        assertEquals("甲", subject.getCorrectAnswer());
        assertEquals(Integer.valueOf(12), subject.getScore());
        assertEquals("展示运行结果", subject.getScreenshotRequirement());
        verify(subjectMapper).insert(subject);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsChoiceWithLessThanTwoDifferentOptions() {
        AdminSubjectRequest request = request("multiple_choice");
        request.setOptions(Arrays.asList("重复", " 重复 "));

        service.create(request);
    }

    @Test
    public void validatesAndOrdersMultipleChoiceAnswer() {
        Subject subject = new Subject();
        subject.setSubjectType("multiple_choice");
        subject.setSubjectOptions("[\"A\",\"B\",\"C\"]");
        service.prepareForResponse(subject);

        String answer = service.validateAnswer(subject, "[\"C\",\"A\",\"C\"]");

        assertEquals("[\"A\",\"C\"]", answer);
    }

    @Test
    public void gradesMultipleChoiceWithoutDependingOnSelectionOrder() {
        Subject subject = new Subject();
        subject.setSubjectType("multiple_choice");
        subject.setSubjectOptions("[\"A\",\"B\",\"C\"]");
        subject.setCorrectAnswer("[\"A\",\"C\"]");
        subject.setScore(8);
        service.prepareForResponse(subject);

        assertEquals(8, service.objectiveScore(subject, "[\"C\",\"A\"]"));
        assertEquals(0, service.objectiveScore(subject, "[\"A\"]"));
    }

    @Test
    public void gradesFillBlankAfterTrimmingEndsAndKeepsCaseSensitive() {
        Subject subject = new Subject();
        subject.setSubjectType("fill_blank");
        subject.setCorrectAnswer("TensorFlow 1.15");
        subject.setScore(6);

        assertEquals(6, service.objectiveScore(subject, "  TensorFlow 1.15  "));
        assertEquals(0, service.objectiveScore(subject, "tensorflow 1.15"));
    }

    @Test
    public void exposesFixedTrueFalseOptions() {
        Subject subject = new Subject();
        subject.setSubjectType("true_false");

        service.prepareForResponse(subject);

        assertEquals(Arrays.asList("正确", "错误"), subject.getOptions());
        assertEquals("正确", service.validateAnswer(subject, "正确"));
    }

    @Test
    public void allowsOptionalEnvironmentForAnyQuestionType() {
        AdminSubjectRequest request = request("fill_blank");
        request.setPoint("code");

        Subject subject = service.create(request);

        assertEquals("code", subject.getPoint());
    }

    @Test
    public void preventsChangingTypeAfterAnswerExists() {
        Subject existing = new Subject();
        existing.setSubjectId(7);
        existing.setTestPaperType("A");
        existing.setSubjectType("fill_blank");
        when(subjectMapper.selectById(7)).thenReturn(existing);
        when(answerSheetMapper.selectCount(any(QueryWrapper.class))).thenReturn(1);

        try {
            service.update(7, request("single_choice"));
            fail("应拒绝修改已有作答题目的题型");
        } catch (IllegalArgumentException exception) {
            assertTrue(exception.getMessage().contains("不能修改试卷或题型"));
        }

        verify(subjectMapper, never()).updateById(any(Subject.class));
    }

    @Test
    public void clearsAllAnswersWithoutDeletingSubjects() {
        when(answerSheetMapper.delete(any())).thenReturn(6);

        int deleted = service.clearAnswers();

        assertEquals(6, deleted);
        verify(answerSheetMapper).delete(any());
        verify(subjectMapper, never()).delete(any());
    }

    @Test
    public void clearsAnswersBeforeAllSubjects() {
        when(answerSheetMapper.delete(any())).thenReturn(6);
        when(subjectMapper.delete(any())).thenReturn(5);

        Map<String, Integer> deleted = service.clearAll();

        assertEquals(Integer.valueOf(6), deleted.get("answerCount"));
        assertEquals(Integer.valueOf(5), deleted.get("subjectCount"));
        InOrder deletionOrder = inOrder(answerSheetMapper, subjectMapper);
        deletionOrder.verify(answerSheetMapper).delete(any());
        deletionOrder.verify(subjectMapper).delete(any());
    }

    private AdminSubjectRequest request(String type) {
        AdminSubjectRequest request = new AdminSubjectRequest();
        request.setTestPaperType("A");
        request.setSubjectType(type);
        request.setSubjectName("测试题目");
        request.setModular("1");
        request.setModularName("模块一");
        request.setSortOrder(1);
        request.setScore(12);
        request.setScreenshotRequirement(" 展示运行结果 ");
        if ("single_choice".equals(type)) {
            request.setOptions(Arrays.asList("A", "B"));
            request.setCorrectAnswer("A");
        } else if ("multiple_choice".equals(type)) {
            request.setOptions(Arrays.asList("A", "B"));
            request.setCorrectAnswer("[\"A\"]");
        } else if ("true_false".equals(type)) {
            request.setCorrectAnswer("正确");
        } else if ("fill_blank".equals(type)) {
            request.setCorrectAnswer("标准答案");
        }
        return request;
    }
}
