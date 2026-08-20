package com.match.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.match.dto.GradingScoresRequest;
import com.match.entity.AnswerSheet;
import com.match.entity.PaperSubmission;
import com.match.entity.PaperSubmissionAnswer;
import com.match.entity.Subject;
import com.match.entity.SubjectType;
import com.match.entity.User;
import com.match.mapper.AnswerSheetMapper;
import com.match.mapper.PaperSubmissionAnswerMapper;
import com.match.mapper.PaperSubmissionMapper;
import com.match.mapper.TestPaperMapper;
import com.match.mapper.UserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PaperGradingService {
    public static final String PENDING_GRADING = "PENDING_GRADING";
    public static final String GRADED = "GRADED";
    public static final String RETURNED = "RETURNED";
    public static final String AUTO = "AUTO";
    public static final String MANUAL = "MANUAL";

    private final PaperSubmissionMapper submissionMapper;
    private final PaperSubmissionAnswerMapper submissionAnswerMapper;
    private final TestPaperMapper subjectMapper;
    private final AnswerSheetMapper answerSheetMapper;
    private final UserMapper userMapper;
    private final PaperCatalogService paperCatalogService;
    private final SubjectManagementService subjectService;

    public PaperGradingService(PaperSubmissionMapper submissionMapper,
                               PaperSubmissionAnswerMapper submissionAnswerMapper,
                               TestPaperMapper subjectMapper,
                               AnswerSheetMapper answerSheetMapper,
                               UserMapper userMapper,
                               PaperCatalogService paperCatalogService,
                               SubjectManagementService subjectService) {
        this.submissionMapper = submissionMapper;
        this.submissionAnswerMapper = submissionAnswerMapper;
        this.subjectMapper = subjectMapper;
        this.answerSheetMapper = answerSheetMapper;
        this.userMapper = userMapper;
        this.paperCatalogService = paperCatalogService;
        this.subjectService = subjectService;
    }

    @Transactional
    public Map<String, Object> submit(Integer userId, String paperType) {
        String paper = paperCatalogService.requireRegistered(paperType);
        User submittingUser = userMapper.selectById(userId);
        if (submittingUser == null) {
            throw new IllegalArgumentException("当前用户不存在");
        }
        PaperSubmission current = current(userId, paper);
        if (current != null && !RETURNED.equals(current.getStatus())) {
            throw new IllegalArgumentException("试卷已经正式提交，不能重复提交");
        }
        if (current != null) {
            current.setCurrentFlag(null);
            submissionMapper.updateById(current);
        }

        List<Subject> subjects = subjects(paper);
        if (subjects.isEmpty()) {
            throw new IllegalArgumentException("当前试卷暂无题目");
        }
        Map<Integer, AnswerSheet> answers = answers(userId).stream()
                .collect(Collectors.toMap(AnswerSheet::getSubjectId, Function.identity(), (first, second) -> second));
        for (Subject subject : subjects) {
            AnswerSheet answer = answers.get(subject.getSubjectId());
            if (!SubjectType.PRACTICAL.getCode().equals(subject.getSubjectType())
                    && !StringUtils.hasText(subject.getCorrectAnswer())) {
                throw new IllegalArgumentException("题目“" + subject.getSubjectName() + "”尚未配置标准答案");
            }
        }

        Date now = new Date();
        PaperSubmission submission = new PaperSubmission();
        submission.setUserId(userId);
        submission.setUserName(submittingUser.getUserName());
        submission.setPaperType(paper);
        submission.setRevision(nextRevision(userId, paper));
        submission.setCurrentFlag(1);
        submission.setObjectiveScore(0);
        submission.setSubmittedAt(now);
        boolean hasPractical = subjects.stream()
                .anyMatch(subject -> SubjectType.PRACTICAL.getCode().equals(subject.getSubjectType()));
        submission.setStatus(hasPractical ? PENDING_GRADING : GRADED);
        if (!hasPractical) {
            submission.setPracticalScore(0);
            submission.setGradedAt(now);
        }
        try {
            submissionMapper.insert(submission);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("试卷已经正式提交，不能重复提交");
        }

        int objectiveScore = 0;
        for (Subject subject : subjects) {
            AnswerSheet answer = answers.get(subject.getSubjectId());
            PaperSubmissionAnswer snapshot = snapshot(submission.getSubmissionId(), subject, answer);
            if (AUTO.equals(snapshot.getGradingMethod())) {
                int awarded = answer == null || !StringUtils.hasText(answer.getAnswerText())
                        ? 0 : subjectService.objectiveScore(subject, answer.getAnswerText());
                snapshot.setAwardedScore(awarded);
                objectiveScore += awarded;
            }
            submissionAnswerMapper.insert(snapshot);
        }
        submission.setObjectiveScore(objectiveScore);
        if (!hasPractical) {
            submission.setTotalScore(objectiveScore);
        }
        submissionMapper.updateById(submission);
        return submissionView(submission, submittingUser);
    }

    public Map<String, Object> participantStatus(Integer userId, String paperType) {
        String paper = paperCatalogService.requireRegistered(paperType);
        PaperSubmission submission = current(userId, paper);
        if (submission == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("submitted", false);
            empty.put("locked", false);
            empty.put("status", "DRAFT");
            return empty;
        }
        Map<String, Object> view = submissionView(submission, null);
        view.put("submitted", !RETURNED.equals(submission.getStatus()));
        view.put("locked", !RETURNED.equals(submission.getStatus()));
        return view;
    }

    public void requireEditable(Integer userId, String paperType) {
        PaperSubmission current = current(userId, paperType);
        if (current != null && !RETURNED.equals(current.getStatus())) {
            throw new IllegalArgumentException("试卷已经正式提交，不能继续修改答案");
        }
    }

    public Map<String, Object> list(String paperType, String status, String keyword) {
        String paper = paperCatalogService.requireRegistered(paperType);
        List<PaperSubmission> all = submissionMapper.selectList(
                new QueryWrapper<PaperSubmission>().eq("paper_type", paper)
                        .orderByAsc("user_id").orderByDesc("revision"));
        Map<Integer, PaperSubmission> latestByUser = new LinkedHashMap<>();
        for (PaperSubmission submission : all) {
            latestByUser.putIfAbsent(submission.getUserId(), submission);
        }
        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put("submitted", (int) latestByUser.values().stream().filter(item -> !RETURNED.equals(item.getStatus())).count());
        summary.put("pending", countStatus(latestByUser.values(), PENDING_GRADING));
        summary.put("graded", countStatus(latestByUser.values(), GRADED));
        summary.put("returned", countStatus(latestByUser.values(), RETURNED));

        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
        List<Map<String, Object>> items = latestByUser.values().stream()
                .filter(item -> !StringUtils.hasText(status) || status.equals(item.getStatus()))
                .filter(item -> {
                    return normalizedKeyword.isEmpty() || (item.getUserName() != null
                            && item.getUserName().toLowerCase().contains(normalizedKeyword));
                })
                .map(item -> submissionView(item, null))
                .collect(Collectors.toList());

        List<PaperSubmission> currentSubmissions = currentForPaper(paper);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("items", items);
        result.put("exportReady", !currentSubmissions.isEmpty()
                && currentSubmissions.stream().allMatch(item -> GRADED.equals(item.getStatus())));
        result.put("exportCount", currentSubmissions.size());
        return result;
    }

    public Map<String, Object> detail(Integer submissionId) {
        PaperSubmission submission = requireSubmission(submissionId);
        Map<String, Object> result = submissionView(submission, null);
        result.put("answers", answersForSubmission(submissionId));
        return result;
    }

    @Transactional
    public Map<String, Object> saveScores(Integer submissionId, GradingScoresRequest request) {
        PaperSubmission submission = requirePending(submissionId);
        if (request == null || request.getScores() == null) {
            throw new IllegalArgumentException("判分内容不能为空");
        }
        Map<Integer, PaperSubmissionAnswer> practical = answersForSubmission(submissionId).stream()
                .filter(answer -> MANUAL.equals(answer.getGradingMethod()))
                .collect(Collectors.toMap(PaperSubmissionAnswer::getSubmissionAnswerId, Function.identity()));
        for (GradingScoresRequest.Item item : request.getScores()) {
            PaperSubmissionAnswer answer = practical.get(item.getSubmissionAnswerId());
            if (answer == null) {
                throw new IllegalArgumentException("只能修改当前提交的实操题分数");
            }
            validateScore(answer, item.getScore());
            answer.setAwardedScore(item.getScore());
            submissionAnswerMapper.updateById(answer);
        }
        return detail(submission.getSubmissionId());
    }

    @Transactional
    public Map<String, Object> complete(Integer submissionId) {
        PaperSubmission submission = requirePending(submissionId);
        List<PaperSubmissionAnswer> answers = answersForSubmission(submissionId);
        int practicalScore = 0;
        for (PaperSubmissionAnswer answer : answers) {
            if (!MANUAL.equals(answer.getGradingMethod())) {
                continue;
            }
            if (answer.getAwardedScore() == null) {
                throw new IllegalArgumentException("请完成所有实操题判分");
            }
            validateScore(answer, answer.getAwardedScore());
            practicalScore += answer.getAwardedScore();
        }
        submission.setPracticalScore(practicalScore);
        submission.setTotalScore(safe(submission.getObjectiveScore()) + practicalScore);
        submission.setStatus(GRADED);
        submission.setGradedAt(new Date());
        submissionMapper.updateById(submission);
        return detail(submissionId);
    }

    @Transactional
    public Map<String, Object> returnForRevision(Integer submissionId, String reason) {
        PaperSubmission submission = requireSubmission(submissionId);
        if (RETURNED.equals(submission.getStatus())) {
            throw new IllegalArgumentException("该试卷已经退回");
        }
        if (!StringUtils.hasText(reason)) {
            throw new IllegalArgumentException("请填写退回原因");
        }
        submission.setStatus(RETURNED);
        submission.setReturnedAt(new Date());
        submission.setReturnReason(reason.trim());
        submissionMapper.updateById(submission);
        return detail(submissionId);
    }

    public List<PaperSubmission> currentForPaper(String paperType) {
        return submissionMapper.selectList(new QueryWrapper<PaperSubmission>()
                .eq("paper_type", paperType).eq("current_flag", 1).orderByAsc("user_id"));
    }

    public PaperSubmission requireSubmission(Integer submissionId) {
        PaperSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new IllegalArgumentException("提交记录不存在");
        }
        return submission;
    }

    public List<PaperSubmissionAnswer> answersForSubmission(Integer submissionId) {
        return submissionAnswerMapper.selectList(new QueryWrapper<PaperSubmissionAnswer>()
                .eq("submission_id", submissionId)
                .orderByAsc("modular", "sort_order", "submission_answer_id"));
    }

    private PaperSubmission requirePending(Integer submissionId) {
        PaperSubmission submission = requireSubmission(submissionId);
        if (!PENDING_GRADING.equals(submission.getStatus())) {
            throw new IllegalArgumentException("当前提交不在待判分状态");
        }
        return submission;
    }

    private PaperSubmissionAnswer snapshot(Integer submissionId, Subject subject, AnswerSheet answer) {
        PaperSubmissionAnswer snapshot = new PaperSubmissionAnswer();
        snapshot.setSubmissionId(submissionId);
        snapshot.setSubjectId(subject.getSubjectId());
        snapshot.setSubjectType(subject.getSubjectType());
        snapshot.setModular(subject.getModular());
        snapshot.setModularName(subject.getModularName());
        snapshot.setSortOrder(subject.getSortOrder() == null ? 0 : subject.getSortOrder());
        snapshot.setSubjectName(subject.getSubjectName());
        snapshot.setSubjectOptions(subject.getSubjectOptions());
        snapshot.setAnswering(subject.getAnswering());
        snapshot.setScreenshotRequirement(subject.getScreenshotRequirement());
        snapshot.setMaxScore(subject.getScore() == null ? 0 : subject.getScore());
        snapshot.setCorrectAnswer(subject.getCorrectAnswer());
        snapshot.setAnswerText(answer == null || answer.getAnswerText() == null ? "" : answer.getAnswerText());
        snapshot.setAnswerImg(answer == null || answer.getAnswerImg() == null ? "" : answer.getAnswerImg());
        snapshot.setGradingMethod(SubjectType.PRACTICAL.getCode().equals(subject.getSubjectType()) ? MANUAL : AUTO);
        return snapshot;
    }

    private List<Subject> subjects(String paper) {
        List<Subject> subjects = subjectMapper.selectList(new QueryWrapper<Subject>()
                .eq("test_paper_type", paper).orderByAsc("modular", "sort_order", "subject_id"));
        subjects.forEach(subjectService::prepareForResponse);
        return subjects;
    }

    private List<AnswerSheet> answers(Integer userId) {
        return answerSheetMapper.selectList(new QueryWrapper<AnswerSheet>().eq("user_id", userId));
    }

    private PaperSubmission current(Integer userId, String paper) {
        return submissionMapper.selectOne(new QueryWrapper<PaperSubmission>()
                .eq("user_id", userId).eq("paper_type", paper).eq("current_flag", 1));
    }

    private PaperSubmission latest(Integer userId, String paper) {
        List<PaperSubmission> submissions = submissionMapper.selectList(new QueryWrapper<PaperSubmission>()
                .eq("user_id", userId).eq("paper_type", paper).orderByDesc("revision").last("LIMIT 1"));
        return submissions.isEmpty() ? null : submissions.get(0);
    }

    private int nextRevision(Integer userId, String paper) {
        PaperSubmission latest = latest(userId, paper);
        return latest == null ? 1 : latest.getRevision() + 1;
    }

    private int countStatus(Iterable<PaperSubmission> submissions, String status) {
        int count = 0;
        for (PaperSubmission submission : submissions) {
            if (status.equals(submission.getStatus())) {
                count++;
            }
        }
        return count;
    }

    private void validateScore(PaperSubmissionAnswer answer, Integer score) {
        if (score == null) {
            return;
        }
        if (score < 0 || score > safe(answer.getMaxScore())) {
            throw new IllegalArgumentException("实操题得分必须在 0 到满分之间");
        }
    }

    private Map<String, Object> submissionView(PaperSubmission submission, User user) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("submissionId", submission.getSubmissionId());
        view.put("userId", submission.getUserId());
        view.put("userName", user == null ? submission.getUserName() : user.getUserName());
        view.put("paperType", submission.getPaperType());
        view.put("revision", submission.getRevision());
        view.put("status", submission.getStatus());
        view.put("objectiveScore", submission.getObjectiveScore());
        view.put("practicalScore", submission.getPracticalScore());
        view.put("totalScore", submission.getTotalScore());
        view.put("submittedAt", submission.getSubmittedAt());
        view.put("gradedAt", submission.getGradedAt());
        view.put("returnedAt", submission.getReturnedAt());
        view.put("returnReason", submission.getReturnReason());
        return view;
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }
}
