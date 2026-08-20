package com.match.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.match.dto.AdminSubjectRequest;
import com.match.entity.AnswerSheet;
import com.match.entity.Subject;
import com.match.entity.SubjectType;
import com.match.mapper.AnswerSheetMapper;
import com.match.mapper.TestPaperMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SubjectManagementService {
    private static final Set<String> PRACTICAL_POINTS =
            new LinkedHashSet<>(Arrays.asList("code", "cvat", "t100"));

    private final TestPaperMapper subjectMapper;
    private final AnswerSheetMapper answerSheetMapper;
    private final PaperCatalogService paperCatalogService;

    public SubjectManagementService(TestPaperMapper subjectMapper, AnswerSheetMapper answerSheetMapper,
                                    PaperCatalogService paperCatalogService) {
        this.subjectMapper = subjectMapper;
        this.answerSheetMapper = answerSheetMapper;
        this.paperCatalogService = paperCatalogService;
    }

    public List<Subject> list(String paperType, String subjectType, String keyword) {
        String paper = normalizePaper(paperType);
        QueryWrapper<Subject> query = new QueryWrapper<>();
        query.eq("test_paper_type", paper);
        if (StringUtils.hasText(subjectType)) {
            query.eq("subject_type", SubjectType.fromCode(subjectType.trim()).getCode());
        }
        if (StringUtils.hasText(keyword)) {
            query.like("subject_name", keyword.trim());
        }
        query.orderByAsc("modular", "sort_order", "subject_id");
        List<Subject> subjects = subjectMapper.selectList(query);
        subjects.forEach(this::prepareForResponse);
        return subjects;
    }

    public Subject create(AdminSubjectRequest request) {
        Subject subject = new Subject();
        apply(subject, request);
        subjectMapper.insert(subject);
        return prepareForResponse(subject);
    }

    public Subject update(Integer subjectId, AdminSubjectRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("题目内容不能为空");
        }
        Subject subject = requireSubject(subjectId);
        if (hasAnswers(subjectId)) {
            String requestedPaper = normalizePaper(request.getTestPaperType());
            String requestedType = SubjectType.fromCode(trim(request.getSubjectType())).getCode();
            if (!subject.getTestPaperType().equals(requestedPaper) || !subject.getSubjectType().equals(requestedType)) {
                throw new IllegalArgumentException("该题已有作答记录，不能修改试卷或题型");
            }
        }
        apply(subject, request);
        subjectMapper.updateById(subject);
        return prepareForResponse(subject);
    }

    public void delete(Integer subjectId) {
        requireSubject(subjectId);
        if (hasAnswers(subjectId)) {
            throw new IllegalArgumentException("该题已有作答记录，不能删除");
        }
        subjectMapper.deleteById(subjectId);
    }

    @Transactional
    public int clearAnswers() {
        return answerSheetMapper.delete(Wrappers.<AnswerSheet>lambdaQuery());
    }

    @Transactional
    public Map<String, Integer> clearAll() {
        int answerCount = answerSheetMapper.delete(Wrappers.<AnswerSheet>lambdaQuery());
        int subjectCount = subjectMapper.delete(Wrappers.<Subject>lambdaQuery());
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("answerCount", answerCount);
        result.put("subjectCount", subjectCount);
        return result;
    }

    public Subject requireSubject(Integer subjectId) {
        if (subjectId == null) {
            throw new IllegalArgumentException("题目 ID 不能为空");
        }
        Subject subject = subjectMapper.selectById(subjectId);
        if (subject == null) {
            throw new IllegalArgumentException("题目不存在");
        }
        return prepareForResponse(subject);
    }

    public int countByPaper(String paperType) {
        QueryWrapper<Subject> query = new QueryWrapper<>();
        query.eq("test_paper_type", normalizePaper(paperType));
        return subjectMapper.selectCount(query);
    }

    public Subject prepareForResponse(Subject subject) {
        SubjectType type = SubjectType.fromCode(subject.getSubjectType());
        if (type == SubjectType.TRUE_FALSE) {
            subject.setOptions(Arrays.asList("正确", "错误"));
        } else if (type == SubjectType.SINGLE_CHOICE || type == SubjectType.MULTIPLE_CHOICE) {
            subject.setOptions(parseOptions(subject.getSubjectOptions()));
        } else {
            subject.setOptions(Collections.emptyList());
        }
        return subject;
    }

    public String validateAnswer(Subject subject, String answerText) {
        SubjectType type = SubjectType.fromCode(subject.getSubjectType());
        if (type == SubjectType.PRACTICAL) {
            return "";
        }
        if (!StringUtils.hasText(answerText)) {
            throw new IllegalArgumentException("请先填写答案");
        }
        if (type == SubjectType.SINGLE_CHOICE || type == SubjectType.TRUE_FALSE) {
            String answer = answerText.trim();
            if (!subject.getOptions().contains(answer)) {
                throw new IllegalArgumentException("答案不属于当前题目选项");
            }
            return answer;
        }
        if (type == SubjectType.MULTIPLE_CHOICE) {
            List<String> answers;
            try {
                answers = JSON.parseArray(answerText, String.class);
            } catch (Exception exception) {
                throw new IllegalArgumentException("多选题答案格式不正确");
            }
            if (answers == null || answers.isEmpty()) {
                throw new IllegalArgumentException("请至少选择一个答案");
            }
            Set<String> selected = new LinkedHashSet<>(answers);
            if (!subject.getOptions().containsAll(selected)) {
                throw new IllegalArgumentException("答案包含无效选项");
            }
            List<String> ordered = new ArrayList<>();
            for (String option : subject.getOptions()) {
                if (selected.contains(option)) {
                    ordered.add(option);
                }
            }
            return JSON.toJSONString(ordered);
        }
        return answerText.trim();
    }

    public int objectiveScore(Subject subject, String answerText) {
        SubjectType type = SubjectType.fromCode(subject.getSubjectType());
        if (type == SubjectType.PRACTICAL) {
            throw new IllegalArgumentException("实操题不能自动判分");
        }
        String normalizedAnswer = validateAnswer(subject, answerText);
        String correctAnswer = subject.getCorrectAnswer();
        boolean correct;
        if (type == SubjectType.MULTIPLE_CHOICE) {
            correct = parseAnswerSet(normalizedAnswer).equals(parseAnswerSet(correctAnswer));
        } else {
            correct = normalizedAnswer.trim().equals(correctAnswer == null ? "" : correctAnswer.trim());
        }
        return correct ? (subject.getScore() == null ? 0 : subject.getScore()) : 0;
    }

    private void apply(Subject subject, AdminSubjectRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("题目内容不能为空");
        }
        SubjectType type = SubjectType.fromCode(trim(request.getSubjectType()));
        String subjectName = trim(request.getSubjectName());
        if (!StringUtils.hasText(subjectName)) {
            throw new IllegalArgumentException("题干不能为空");
        }
        String modular = trim(request.getModular());
        String modularName = trim(request.getModularName());
        if (!StringUtils.hasText(modular) || !StringUtils.hasText(modularName)) {
            throw new IllegalArgumentException("模块序号和模块名称不能为空");
        }
        if (request.getSortOrder() != null && request.getSortOrder() < 0) {
            throw new IllegalArgumentException("题目顺序不能小于 0");
        }
        if (request.getScore() != null && request.getScore() < 0) {
            throw new IllegalArgumentException("题目分值不能小于 0");
        }
        List<String> options = normalizeOptions(request.getOptions());
        if (type == SubjectType.SINGLE_CHOICE || type == SubjectType.MULTIPLE_CHOICE) {
            if (options.size() < 2) {
                throw new IllegalArgumentException("单选题和多选题至少需要两个不同选项");
            }
            subject.setSubjectOptions(JSON.toJSONString(options));
        } else {
            subject.setSubjectOptions(null);
        }
        subject.setCorrectAnswer(normalizeCorrectAnswer(type, request.getCorrectAnswer(), options));
        String point = trim(request.getPoint());
        if (StringUtils.hasText(point)) {
            if (!PRACTICAL_POINTS.contains(point)) {
                throw new IllegalArgumentException("答题环境只能选择 code、cvat 或 t100");
            }
            subject.setPoint(point);
        } else {
            subject.setPoint(null);
        }
        subject.setTestPaperType(normalizePaper(request.getTestPaperType()));
        subject.setSubjectType(type.getCode());
        subject.setSubjectName(subjectName);
        subject.setScore(request.getScore() == null ? 0 : request.getScore());
        subject.setAnswering(trim(request.getAnswering()));
        subject.setScreenshotRequirement(trim(request.getScreenshotRequirement()));
        subject.setModular(modular);
        subject.setModularName(modularName);
        subject.setSubjectIdentification(trim(request.getSubjectIdentification()));
        subject.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
    }

    private boolean hasAnswers(Integer subjectId) {
        QueryWrapper<AnswerSheet> query = new QueryWrapper<>();
        query.eq("subject_id", subjectId);
        return answerSheetMapper.selectCount(query) > 0;
    }

    private List<String> parseOptions(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            List<String> options = JSON.parseArray(json, String.class);
            return options == null ? Collections.emptyList() : options;
        } catch (Exception exception) {
            throw new IllegalStateException("题目选项数据格式错误", exception);
        }
    }

    private List<String> normalizeOptions(List<String> options) {
        if (options == null) {
            return Collections.emptyList();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String option : options) {
            String value = trim(option);
            if (StringUtils.hasText(value)) {
                normalized.add(value);
            }
        }
        return new ArrayList<>(normalized);
    }

    private String normalizeCorrectAnswer(SubjectType type, String answer, List<String> options) {
        if (type == SubjectType.PRACTICAL) {
            return null;
        }
        if (!StringUtils.hasText(answer)) {
            throw new IllegalArgumentException("客观题必须配置标准答案");
        }
        if (type == SubjectType.MULTIPLE_CHOICE) {
            Set<String> selected = parseAnswerSet(answer);
            if (selected.isEmpty() || !options.containsAll(selected)) {
                throw new IllegalArgumentException("多选题标准答案包含无效选项");
            }
            List<String> ordered = new ArrayList<>();
            for (String option : options) {
                if (selected.contains(option)) {
                    ordered.add(option);
                }
            }
            return JSON.toJSONString(ordered);
        }
        String normalized = answer.trim();
        if ((type == SubjectType.SINGLE_CHOICE || type == SubjectType.TRUE_FALSE)
                && !effectiveOptions(type, options).contains(normalized)) {
            throw new IllegalArgumentException("标准答案不属于当前题目选项");
        }
        return normalized;
    }

    private Set<String> parseAnswerSet(String answer) {
        try {
            List<String> values = JSON.parseArray(answer, String.class);
            if (values == null) {
                return Collections.emptySet();
            }
            Set<String> normalized = new LinkedHashSet<>();
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    normalized.add(value.trim());
                }
            }
            return normalized;
        } catch (Exception exception) {
            throw new IllegalArgumentException("多选题标准答案格式不正确");
        }
    }

    private List<String> effectiveOptions(SubjectType type, List<String> options) {
        if (type == SubjectType.TRUE_FALSE) {
            return Arrays.asList("正确", "错误");
        }
        return options;
    }

    private String normalizePaper(String paperType) {
        return paperCatalogService.requireRegistered(paperType);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
