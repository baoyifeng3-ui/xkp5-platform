package com.match.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.match.entity.AnswerSheet;
import com.match.entity.PaperSubmission;
import com.match.entity.Score;
import com.match.mapper.AnswerSheetMapper;
import com.match.mapper.PaperSubmissionMapper;
import com.match.mapper.ScoreMapper;
import org.springframework.stereotype.Service;

@Service
public class CompetitionRoundResetService {
    private final AnswerSheetMapper answerSheetMapper;
    private final ScoreMapper scoreMapper;
    private final PaperSubmissionMapper submissionMapper;

    public CompetitionRoundResetService(AnswerSheetMapper answerSheetMapper,
                                        ScoreMapper scoreMapper,
                                        PaperSubmissionMapper submissionMapper) {
        this.answerSheetMapper = answerSheetMapper;
        this.scoreMapper = scoreMapper;
        this.submissionMapper = submissionMapper;
    }

    public void reset() {
        answerSheetMapper.delete(new QueryWrapper<AnswerSheet>());
        scoreMapper.delete(new QueryWrapper<Score>());
        submissionMapper.update(null, new UpdateWrapper<PaperSubmission>()
                .eq("current_flag", 1)
                .setSql("current_flag = NULL"));
    }
}
