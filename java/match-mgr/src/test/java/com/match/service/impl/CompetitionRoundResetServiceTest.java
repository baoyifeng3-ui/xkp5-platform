package com.match.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.match.entity.AnswerSheet;
import com.match.entity.PaperSubmission;
import com.match.entity.Score;
import com.match.mapper.AnswerSheetMapper;
import com.match.mapper.PaperSubmissionMapper;
import com.match.mapper.ScoreMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class CompetitionRoundResetServiceTest {
    @Mock private AnswerSheetMapper answerSheetMapper;
    @Mock private ScoreMapper scoreMapper;
    @Mock private PaperSubmissionMapper submissionMapper;
    private CompetitionRoundResetService service;

    @Before
    public void setUp() {
        service = new CompetitionRoundResetService(answerSheetMapper, scoreMapper, submissionMapper);
    }

    @Test
    public void clearsDraftsScoresAndCurrentSubmissionMarkers() {
        service.reset();

        verify(answerSheetMapper).delete(any(Wrapper.class));
        verify(scoreMapper).delete(any(Wrapper.class));
        verify(submissionMapper).update(isNull(PaperSubmission.class), any(Wrapper.class));
    }
}
