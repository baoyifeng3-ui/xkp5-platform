package com.match.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.match.dto.GradingScoresRequest;
import com.match.entity.AnswerSheet;
import com.match.entity.PaperSubmission;
import com.match.entity.PaperSubmissionAnswer;
import com.match.entity.Subject;
import com.match.entity.User;
import com.match.mapper.AnswerSheetMapper;
import com.match.mapper.PaperSubmissionAnswerMapper;
import com.match.mapper.PaperSubmissionMapper;
import com.match.mapper.TestPaperMapper;
import com.match.mapper.UserMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PaperGradingServiceTest {
    @Mock private PaperSubmissionMapper submissionMapper;
    @Mock private PaperSubmissionAnswerMapper submissionAnswerMapper;
    @Mock private TestPaperMapper subjectMapper;
    @Mock private AnswerSheetMapper answerSheetMapper;
    @Mock private UserMapper userMapper;
    @Mock private PaperCatalogService paperCatalogService;
    @Mock private SubjectManagementService subjectService;

    private PaperGradingService service;

    @Before
    public void setUp() {
        when(paperCatalogService.requireRegistered("A")).thenReturn("A");
        service = new PaperGradingService(submissionMapper, submissionAnswerMapper, subjectMapper,
                answerSheetMapper, userMapper, paperCatalogService, subjectService);
    }

    @Test
    public void submitsSnapshotAndAutoGradesObjectiveQuestion() {
        stubUser();
        Subject objective = subject(1, "single_choice", 5, "A");
        Subject practical = subject(2, "practical", 10, null);
        AnswerSheet objectiveAnswer = answer(1, "A", null);
        AnswerSheet practicalAnswer = answer(2, "", "/files/group/image.jpg");
        when(subjectMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(objective, practical));
        when(answerSheetMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(objectiveAnswer, practicalAnswer));
        when(submissionMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        when(subjectService.objectiveScore(objective, "A")).thenReturn(5);
        doAnswer(invocation -> {
            PaperSubmission submission = invocation.getArgument(0);
            submission.setSubmissionId(12);
            return 1;
        }).when(submissionMapper).insert(any(PaperSubmission.class));

        Map<String, Object> result = service.submit(7, "A");

        assertEquals("PENDING_GRADING", result.get("status"));
        assertEquals(5, result.get("objectiveScore"));
        verify(submissionAnswerMapper, org.mockito.Mockito.times(2)).insert(any(PaperSubmissionAnswer.class));
    }

    @Test
    public void submitsMissingAnswersAsZeroScoreSnapshots() {
        stubUser();
        Subject objective = subject(3, "fill_blank", 4, "Answer");
        when(subjectMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(objective));
        when(answerSheetMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        when(submissionMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        doAnswer(invocation -> {
            PaperSubmission submission = invocation.getArgument(0);
            submission.setSubmissionId(13);
            return 1;
        }).when(submissionMapper).insert(any(PaperSubmission.class));

        Map<String, Object> result = service.submit(7, "A");

        assertEquals("GRADED", result.get("status"));
        assertEquals(0, result.get("objectiveScore"));
        verify(submissionAnswerMapper).insert(any(PaperSubmissionAnswer.class));
    }

    @Test
    public void rejectsPracticalScoreAboveMaximum() {
        PaperSubmission submission = new PaperSubmission();
        submission.setSubmissionId(8);
        submission.setStatus(PaperGradingService.PENDING_GRADING);
        PaperSubmissionAnswer answer = new PaperSubmissionAnswer();
        answer.setSubmissionAnswerId(9);
        answer.setGradingMethod(PaperGradingService.MANUAL);
        answer.setMaxScore(10);
        when(submissionMapper.selectById(8)).thenReturn(submission);
        when(submissionAnswerMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(answer));
        GradingScoresRequest request = new GradingScoresRequest();
        GradingScoresRequest.Item item = new GradingScoresRequest.Item();
        item.setSubmissionAnswerId(9);
        item.setScore(11);
        request.setScores(Collections.singletonList(item));

        try {
            service.saveScores(8, request);
            fail("超过满分时不应保存");
        } catch (IllegalArgumentException exception) {
            assertTrue(exception.getMessage().contains("满分"));
        }
    }

    @Test
    public void returnsDraftWhenOnlyHistoricalGradedSubmissionExists() {
        when(submissionMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        Map<String, Object> result = service.participantStatus(7, "A");

        assertEquals("DRAFT", result.get("status"));
        assertEquals(false, result.get("locked"));
    }

    @Test
    public void keepsReturnedCurrentSubmissionEditable() {
        PaperSubmission returned = new PaperSubmission();
        returned.setSubmissionId(16);
        returned.setStatus(PaperGradingService.RETURNED);
        returned.setCurrentFlag(1);
        returned.setReturnReason("补充截图");
        when(submissionMapper.selectOne(any(QueryWrapper.class))).thenReturn(returned);

        Map<String, Object> result = service.participantStatus(7, "A");

        assertEquals("RETURNED", result.get("status"));
        assertEquals(false, result.get("locked"));
        assertEquals("补充截图", result.get("returnReason"));
    }

    private Subject subject(int id, String type, int score, String correctAnswer) {
        Subject subject = new Subject();
        subject.setSubjectId(id);
        subject.setSubjectType(type);
        subject.setSubjectName("题目" + id);
        subject.setTestPaperType("A");
        subject.setScore(score);
        subject.setCorrectAnswer(correctAnswer);
        return subject;
    }

    private AnswerSheet answer(int subjectId, String text, String image) {
        AnswerSheet answer = new AnswerSheet();
        answer.setSubjectId(subjectId);
        answer.setAnswerText(text);
        answer.setAnswerImg(image);
        return answer;
    }

    private void stubUser() {
        User user = new User();
        user.setUserId(7);
        user.setUserName("user7");
        when(userMapper.selectById(7)).thenReturn(user);
    }
}
