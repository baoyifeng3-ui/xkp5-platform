package com.match.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.match.entity.Score;
import com.match.mapper.ScoreMapper;
import com.match.service.ScoreService;
import com.match.util.decimal.DecimalUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ScoreServiceImpl extends ServiceImpl<ScoreMapper, Score> implements ScoreService {
    private final UserServiceImpl userService;
    private final String pythonExecutable;
    private final String scoringScript;
    private final String annotationsRoot;
    private final long timeoutSeconds;

    public ScoreServiceImpl(UserServiceImpl userService,
                            @Value("${match.scoring.python-executable:python3}") String pythonExecutable,
                            @Value("${match.scoring.script:python/evaluation_score.py}") String scoringScript,
                            @Value("${match.scoring.annotations-root:python}") String annotationsRoot,
                            @Value("${match.scoring.timeout-seconds:30}") long timeoutSeconds) {
        this.userService = userService;
        this.pythonExecutable = pythonExecutable;
        this.scoringScript = scoringScript;
        this.annotationsRoot = Paths.get(annotationsRoot).toAbsolutePath().normalize().toString();
        this.timeoutSeconds = timeoutSeconds;
    }

    public void saveScore(Double score) {
        score = DecimalUtil.round(score, 4);
        Score scorePojo = new Score();
        scorePojo.setUserId(StpUtil.getLoginIdAsInt());
        scorePojo.setScore(score);
        this.save(scorePojo);
    }

    public Score excPy(String annotation, String paperType) throws Exception {
        if (annotation == null || annotation.trim().isEmpty()) {
            throw new IllegalArgumentException("评分数据不能为空");
        }
        String normalizedPaper = paperType == null ? "" : paperType.trim().toLowerCase();
        if (!normalizedPaper.matches("[a-z]")) {
            throw new IllegalArgumentException("不支持的赛卷类型");
        }

        ProcessBuilder processBuilder = new ProcessBuilder(pythonExecutable, scoringScript, normalizedPaper)
                .redirectErrorStream(false);
        processBuilder.environment().put("MATCH_SCORING_ANNOTATIONS_ROOT", annotationsRoot);
        Process process = processBuilder.start();
        try {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
                writer.write(annotation);
            }
            StreamReader stdout = new StreamReader(process.getInputStream());
            StreamReader stderr = new StreamReader(process.getErrorStream());
            stdout.start();
            stderr.start();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("评分服务执行超时");
            }
            stdout.join();
            stderr.join();
            if (process.exitValue() != 0) {
                throw new IllegalStateException("评分服务执行失败：" + stderr.text());
            }
            String scoreText = stdout.text().trim();
            if (scoreText.isEmpty()) {
                throw new IllegalStateException("评分服务没有返回结果");
            }
            Score score = new Score();
            score.setScore(Double.parseDouble(scoreText.split("\\R")[scoreText.split("\\R").length - 1].trim()));
            return score;
        } finally {
            process.destroy();
        }
    }

    public void relUser(List<Score> scores) {
        scores.forEach(score -> {
            com.match.entity.User user = userService.getById(score.getUserId());
            if (user != null) {
                user.setPassword(null);
            }
            score.setUser(user);
        });
    }

    private static class StreamReader extends Thread {
        private final InputStream inputStream;
        private final List<String> lines = new ArrayList<>();

        private StreamReader(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            } catch (IOException exception) {
                lines.add(exception.getMessage());
            }
        }

        private String text() {
            return String.join("\n", lines);
        }
    }
}
