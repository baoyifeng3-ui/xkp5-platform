package com.match.service.impl;

import com.match.entity.PaperSubmission;
import com.match.entity.PaperSubmissionAnswer;
import com.match.entity.PaperExportBatch;
import com.match.dto.CountDownResponse;
import com.match.mapper.PaperExportBatchMapper;
import net.lingala.zip4j.io.inputstream.ZipInputStream;
import net.lingala.zip4j.model.LocalFileHeader;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PaperExportServiceTest {
    @Mock private PaperExportBatchMapper exportBatchMapper;
    @Mock private PaperGradingService gradingService;
    @Mock private PaperCatalogService paperCatalogService;
    @Mock private CountDownServiceImpl countDownService;

    private PaperExportService service;

    @Before
    public void setUp() {
        service = new PaperExportService(exportBatchMapper, gradingService, paperCatalogService, countDownService);
    }

    @Test
    public void createsPasswordProtectedZipWithOneNamedPdf() throws Exception {
        PaperSubmission submission = new PaperSubmission();
        submission.setSubmissionId(4);
        submission.setUserId(7);
        submission.setUserName("测试用户");
        submission.setPaperType("A");
        submission.setRevision(1);
        submission.setObjectiveScore(5);
        submission.setPracticalScore(0);
        submission.setTotalScore(5);
        submission.setSubmittedAt(new Date());
        submission.setGradedAt(new Date());
        PaperSubmissionAnswer answer = new PaperSubmissionAnswer();
        answer.setSubmissionAnswerId(8);
        answer.setSubjectType("fill_blank");
        answer.setSubjectName("填写版本号");
        answer.setAnswerText("1.15");
        answer.setAwardedScore(5);
        answer.setMaxScore(5);
        answer.setGradingMethod(PaperGradingService.AUTO);
        when(gradingService.answersForSubmission(4)).thenReturn(Collections.singletonList(answer));

        byte[] archive = service.generateArchive(Collections.singletonList(submission), "Abcd2345Efgh6789");

        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(archive), "Abcd2345Efgh6789".toCharArray())) {
            LocalFileHeader header = zip.getNextEntry();
            assertEquals("测试用户.pdf", header.getFileName());
            ByteArrayOutputStream pdf = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int length;
            while ((length = zip.read(buffer)) >= 0) {
                pdf.write(buffer, 0, length);
            }
            assertTrue(new String(pdf.toByteArray(), 0, 4, StandardCharsets.US_ASCII).startsWith("%PDF"));
        }

        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(archive), "Wrong2345Key6789".toCharArray())) {
            zip.getNextEntry();
            byte[] buffer = new byte[256];
            while (zip.read(buffer) >= 0) {
                // Reading forces password verification.
            }
            fail("错误密钥不应解压成功");
        } catch (Exception expected) {
            assertTrue(expected.getMessage() != null);
        }
    }

    @Test
    public void exportsGradedPracticalAnswerWithoutScreenshot() throws Exception {
        PaperSubmission submission = new PaperSubmission();
        submission.setSubmissionId(5);
        submission.setUserId(8);
        submission.setUserName("未截图用户");
        submission.setPaperType("A");
        submission.setRevision(1);
        submission.setObjectiveScore(0);
        submission.setPracticalScore(0);
        submission.setTotalScore(0);
        submission.setSubmittedAt(new Date());
        submission.setGradedAt(new Date());

        PaperSubmissionAnswer answer = new PaperSubmissionAnswer();
        answer.setSubmissionAnswerId(9);
        answer.setSubjectType("practical");
        answer.setSubjectName("完成实操任务");
        answer.setAnswerImg("");
        answer.setAwardedScore(0);
        answer.setMaxScore(10);
        answer.setGradingMethod(PaperGradingService.MANUAL);
        when(gradingService.answersForSubmission(5)).thenReturn(Arrays.asList(answer));

        byte[] archive = service.generateArchive(Collections.singletonList(submission), "Abcd2345Efgh6789");

        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(archive), "Abcd2345Efgh6789".toCharArray())) {
            assertEquals("未截图用户.pdf", zip.getNextEntry().getFileName());
            byte[] header = new byte[4];
            assertEquals(4, zip.read(header));
            assertEquals("%PDF", new String(header, StandardCharsets.US_ASCII));
        }
    }

    @Test
    public void refusesExportBeforeCompetitionFinishes() {
        CountDownResponse snapshot = new CountDownResponse();
        snapshot.setStatus("RUNNING");
        when(countDownService.snapshot()).thenReturn(snapshot);

        try {
            service.create(new com.match.entity.User(), "A");
            fail("比赛进行中不应允许导出");
        } catch (IllegalArgumentException exception) {
            assertTrue(exception.getMessage().contains("比赛结束"));
        }
    }

    @Test
    public void reportsOnlyCompleteCurrentSubmissionExportAsExported() {
        PaperSubmission first = new PaperSubmission();
        first.setSubmissionId(11);
        PaperSubmission second = new PaperSubmission();
        second.setSubmissionId(12);
        PaperExportBatch partial = new PaperExportBatch();
        partial.setSubmissionIds("[11]");
        when(paperCatalogService.requireRegistered("A")).thenReturn("A");
        when(gradingService.currentForPaper("A")).thenReturn(Arrays.asList(first, second));
        when(exportBatchMapper.selectList(any())).thenReturn(Arrays.asList(partial));

        Map<String, Object> status = service.currentExportStatus("A");

        assertFalse((Boolean) status.get("exported"));
        assertEquals(2, status.get("submissionCount"));
    }
}
