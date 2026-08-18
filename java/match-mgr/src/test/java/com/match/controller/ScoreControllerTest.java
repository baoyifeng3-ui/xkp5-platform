package com.match.controller;

import com.match.entity.Score;
import com.match.service.TeamsService;
import com.match.service.impl.PaperResourceService;
import com.match.service.impl.ScoreServiceImpl;
import com.match.service.impl.SystemSettingService;
import com.match.util.result.ResponseResult;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ScoreControllerTest {
    @Mock
    private ScoreServiceImpl scoreService;

    @Mock
    private SystemSettingService systemSettingService;

    @Mock
    private PaperResourceService paperResourceService;

    @Mock
    private TeamsService teamsService;

    private ScoreController controller;

    @Before
    public void setUp() {
        controller = new ScoreController();
        controller.scoreService = scoreService;
        controller.systemSettingService = systemSettingService;
        controller.paperResourceService = paperResourceService;
        controller.teamsService = teamsService;
    }

    @Test
    public void rejectsClientPaperThatDoesNotMatchActivePaper() throws Exception {
        when(systemSettingService.getActivePaper()).thenReturn("A");

        ResponseResult<Object> response = controller.save("{}", "B");

        assertEquals(400, response.getCode());
        verify(scoreService, never()).excPy("{}", "B");
    }

    @Test
    public void scoresWithServerSideActivePaper() throws Exception {
        Score score = new Score();
        score.setScore(88.0);
        when(systemSettingService.getActivePaper()).thenReturn("C");
        when(scoreService.excPy("{}", "C")).thenReturn(score);

        ResponseResult<Object> response = controller.save("{}", null);

        assertEquals(200, response.getCode());
        verify(paperResourceService).requireReady("C");
        verify(scoreService).excPy("{}", "C");
        verify(scoreService).saveScore(88.0);
    }
}
