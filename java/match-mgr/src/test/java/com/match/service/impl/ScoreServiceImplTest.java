package com.match.service.impl;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;
import static org.mockito.Mockito.mock;

public class ScoreServiceImplTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void selectsAnnotationsFileFromPaperAndRoot() throws Exception {
        // 该用例通过 /bin/sh 解释器验证评分脚本调用链；生产评分在 Ubuntu 容器运行，
        // Windows 开发机不存在 /bin/sh，直接跳过以免误报失败。
        assumeFalse("shell-based scoring test runs on Unix-like systems only",
                System.getProperty("os.name").toLowerCase().contains("win"));
        Path annotationsRoot = temporaryFolder.newFolder("annotations").toPath();
        Files.createDirectories(annotationsRoot.resolve("a"));
        Files.createDirectories(annotationsRoot.resolve("b"));
        Files.write(annotationsRoot.resolve("a/annotations.xml"), "41.5\n".getBytes(StandardCharsets.UTF_8));
        Files.write(annotationsRoot.resolve("b/annotations.xml"), "86.5\n".getBytes(StandardCharsets.UTF_8));
        Path script = temporaryFolder.newFile("score.sh").toPath();
        String body = "#!/bin/sh\n"
                + "cat >/dev/null\n"
                + "cat \"$MATCH_SCORING_ANNOTATIONS_ROOT/$1/annotations.xml\"\n";
        Files.write(script, body.getBytes(StandardCharsets.UTF_8));

        ScoreServiceImpl service = new ScoreServiceImpl(
                mock(UserServiceImpl.class), "/bin/sh", script.toString(), annotationsRoot.toString(), 5);

        assertEquals(41.5, service.excPy("{}", "A").getScore(), 0.0001);
        assertEquals(86.5, service.excPy("{}", "B").getScore(), 0.0001);
    }
}
