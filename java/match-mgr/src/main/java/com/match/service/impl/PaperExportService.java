package com.match.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.match.entity.PaperExportBatch;
import com.match.entity.PaperSubmission;
import com.match.entity.PaperSubmissionAnswer;
import com.match.entity.SubjectType;
import com.match.entity.User;
import com.match.mapper.PaperExportBatchMapper;
import com.match.util.dfs.FastDFSClient;
import net.lingala.zip4j.io.outputstream.ZipOutputStream;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PaperExportService {
    private static final char[] KEY_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();
    private static final String SUCCESS = "SUCCESS";

    private final PaperExportBatchMapper exportBatchMapper;
    private final PaperGradingService gradingService;
    private final PaperCatalogService paperCatalogService;
    private final CountDownServiceImpl countDownService;
    private final SecureRandom secureRandom = new SecureRandom();

    public PaperExportService(PaperExportBatchMapper exportBatchMapper,
                              PaperGradingService gradingService,
                              PaperCatalogService paperCatalogService,
                              CountDownServiceImpl countDownService) {
        this.exportBatchMapper = exportBatchMapper;
        this.gradingService = gradingService;
        this.paperCatalogService = paperCatalogService;
        this.countDownService = countDownService;
    }

    public ExportArtifact create(User admin, String paperType) {
        requireCompetitionFinished();
        String paper = paperCatalogService.requireRegistered(paperType);
        List<PaperSubmission> submissions = gradingService.currentForPaper(paper);
        requireExportable(submissions);
        String key = randomKey();
        byte[] archive = generateArchive(submissions, key);

        PaperExportBatch batch = new PaperExportBatch();
        batch.setPaperType(paper);
        batch.setCreatedBy(admin.getUserId());
        batch.setCreatedByName(admin.getUserName());
        batch.setCreatedAt(new Date());
        batch.setExportKey(key);
        batch.setSubmissionIds(JSON.toJSONString(submissionIds(submissions)));
        batch.setPdfCount(submissions.size());
        batch.setStatus(SUCCESS);
        exportBatchMapper.insert(batch);
        return new ExportArtifact(batch, archive, archiveName(batch.getCreatedAt()));
    }

    public ExportArtifact download(Integer batchId) {
        PaperExportBatch batch = exportBatchMapper.selectById(batchId);
        if (batch == null || !SUCCESS.equals(batch.getStatus())) {
            throw new IllegalArgumentException("导出批次不存在");
        }
        List<Integer> submissionIds = JSON.parseArray(batch.getSubmissionIds(), Integer.class);
        if (submissionIds == null || submissionIds.isEmpty()) {
            throw new IllegalStateException("导出批次没有试卷记录");
        }
        List<PaperSubmission> submissions = new ArrayList<>();
        for (Integer submissionId : submissionIds) {
            submissions.add(gradingService.requireSubmission(submissionId));
        }
        byte[] archive = generateArchive(submissions, batch.getExportKey());
        return new ExportArtifact(batch, archive, archiveName(batch.getCreatedAt()));
    }

    public String paperType(Integer batchId) {
        PaperExportBatch batch = exportBatchMapper.selectById(batchId);
        if (batch == null || !SUCCESS.equals(batch.getStatus())) {
            throw new IllegalArgumentException("导出批次不存在");
        }
        return batch.getPaperType();
    }

    public ExportArtifact requireDownloadable(Integer batchId, String activePaper) {
        PaperExportBatch batch = exportBatchMapper.selectById(batchId);
        if (batch == null || !SUCCESS.equals(batch.getStatus())) {
            throw new IllegalArgumentException("导出批次不存在");
        }
        if (!activePaper.equals(batch.getPaperType())) {
            throw new IllegalArgumentException("只能下载当前启用试卷的导出批次");
        }
        return download(batchId);
    }

    public List<PaperExportBatch> list(String paperType) {
        String paper = paperCatalogService.requireRegistered(paperType);
        return exportBatchMapper.selectList(new QueryWrapper<PaperExportBatch>()
                .eq("paper_type", paper).eq("status", SUCCESS).orderByDesc("created_at", "export_batch_id"));
    }

    public Map<String, Object> currentExportStatus(String paperType) {
        String paper = paperCatalogService.requireRegistered(paperType);
        List<PaperSubmission> submissions = gradingService.currentForPaper(paper);
        Set<Integer> currentIds = new HashSet<>(submissionIds(submissions));
        boolean exported = false;
        if (!currentIds.isEmpty()) {
            for (PaperExportBatch batch : list(paper)) {
                List<Integer> exportedIds = JSON.parseArray(batch.getSubmissionIds(), Integer.class);
                if (exportedIds != null && new HashSet<>(exportedIds).containsAll(currentIds)) {
                    exported = true;
                    break;
                }
            }
        }
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("exported", exported);
        status.put("submissionCount", currentIds.size());
        return status;
    }

    byte[] generateArchive(List<PaperSubmission> submissions, String key) {
        Set<String> usedNames = new HashSet<>();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ZipParameters parameters = new ZipParameters();
        parameters.setCompressionMethod(CompressionMethod.DEFLATE);
        parameters.setEncryptFiles(true);
        parameters.setEncryptionMethod(EncryptionMethod.AES);
        parameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
        try (ZipOutputStream zip = new ZipOutputStream(output, key.toCharArray())) {
            for (PaperSubmission submission : submissions) {
                User user = new User();
                user.setUserId(submission.getUserId());
                user.setUserName(submission.getUserName());
                parameters.setFileNameInZip(uniquePdfName(user, usedNames));
                zip.putNextEntry(parameters);
                zip.write(generatePdf(submission, user));
                zip.closeEntry();
            }
        } catch (Exception exception) {
            throw new IllegalStateException("试卷归档生成失败：" + exception.getMessage(), exception);
        }
        return output.toByteArray();
    }

    private byte[] generatePdf(PaperSubmission submission, User user) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 44, 44, 46, 46);
        PdfWriter.getInstance(document, output);
        document.open();
        BaseFont baseFont = loadFont();
        Font title = new Font(baseFont, 18, Font.BOLD);
        Font heading = new Font(baseFont, 12, Font.BOLD);
        Font body = new Font(baseFont, 10, Font.NORMAL);
        Font muted = new Font(baseFont, 9, Font.NORMAL, new java.awt.Color(80, 87, 96));

        Paragraph titleParagraph = new Paragraph("试卷判分归档", title);
        titleParagraph.setAlignment(Element.ALIGN_CENTER);
        titleParagraph.setSpacingAfter(18);
        document.add(titleParagraph);

        PdfPTable metadata = new PdfPTable(new float[]{1, 2, 1, 2});
        metadata.setWidthPercentage(100);
        addMetadata(metadata, "用户", user.getUserName(), heading, body);
        addMetadata(metadata, "试卷", submission.getPaperType() + " 卷", heading, body);
        addMetadata(metadata, "提交修订", "第 " + submission.getRevision() + " 次", heading, body);
        addMetadata(metadata, "提交时间", formatDate(submission.getSubmittedAt()), heading, body);
        addMetadata(metadata, "判分时间", formatDate(submission.getGradedAt()), heading, body);
        addMetadata(metadata, "总分", safe(submission.getTotalScore()) + " 分", heading, body);
        metadata.setSpacingAfter(18);
        document.add(metadata);

        String previousModule = null;
        int number = 0;
        for (PaperSubmissionAnswer answer : gradingService.answersForSubmission(submission.getSubmissionId())) {
            number++;
            String module = safeText(answer.getModular()) + " " + safeText(answer.getModularName());
            if (!module.equals(previousModule)) {
                Paragraph moduleHeading = new Paragraph("模块 " + module.trim(), heading);
                moduleHeading.setSpacingBefore(8);
                moduleHeading.setSpacingAfter(8);
                document.add(moduleHeading);
                previousModule = module;
            }
            Paragraph question = new Paragraph(number + ". " + safeText(answer.getSubjectName()), heading);
            question.setSpacingAfter(5);
            document.add(question);
            if (SubjectType.PRACTICAL.getCode().equals(answer.getSubjectType())) {
                if (answer.getAnswering() != null && !answer.getAnswering().trim().isEmpty()) {
                    document.add(spaced("作答说明：" + answer.getAnswering(), muted, 3));
                }
                addImages(document, answer.getAnswerImg(), body);
            } else {
                document.add(spaced("用户答案：" + formatAnswer(answer), body, 4));
            }
            Paragraph score = new Paragraph("得分：" + safe(answer.getAwardedScore())
                    + " / " + safe(answer.getMaxScore()), body);
            score.setSpacingAfter(12);
            document.add(score);
        }

        PdfPTable summary = new PdfPTable(new float[]{2, 1});
        summary.setWidthPercentage(52);
        summary.setHorizontalAlignment(Element.ALIGN_RIGHT);
        addSummary(summary, "客观题得分", submission.getObjectiveScore(), heading, body);
        addSummary(summary, "实操题得分", submission.getPracticalScore(), heading, body);
        addSummary(summary, "总分", submission.getTotalScore(), heading, body);
        document.add(summary);
        document.close();
        return output.toByteArray();
    }

    private BaseFont loadFont() throws Exception {
        ClassPathResource resource = new ClassPathResource("fonts/NotoSansCJKsc-Regular.otf");
        byte[] fontBytes;
        try (InputStream input = resource.getInputStream()) {
            fontBytes = readAll(input);
        }
        return BaseFont.createFont("NotoSansCJKsc-Regular.otf", BaseFont.IDENTITY_H,
                BaseFont.EMBEDDED, true, fontBytes, null);
    }

    private void addImages(Document document, String refs, Font body) throws Exception {
        if (refs == null || refs.trim().isEmpty()) {
            document.add(spaced("用户未提交截图", body, 8));
            return;
        }
        for (String value : imageReferences(refs)) {
            String ref = value.trim();
            if (ref.isEmpty()) {
                continue;
            }
            byte[] data = imageBytes(ref);
            Image image = Image.getInstance(data);
            image.scaleToFit(480, 340);
            image.setAlignment(Element.ALIGN_LEFT);
            image.setSpacingAfter(8);
            document.add(image);
        }
    }

    private byte[] imageBytes(String ref) throws IOException {
        if (ref.startsWith("data:image")) {
            int comma = ref.indexOf(',');
            if (comma < 0) {
                throw new IOException("图片数据格式错误");
            }
            return Base64.getDecoder().decode(ref.substring(comma + 1));
        }
        if (!ref.startsWith("/files/") && !ref.startsWith("http://") && !ref.startsWith("https://")) {
            try {
                return Base64.getDecoder().decode(ref.replace("\r", "").replace("\n", ""));
            } catch (IllegalArgumentException exception) {
                throw new IOException("截图数据格式错误", exception);
            }
        }
        String address = FastDFSClient.getServerAccessUrl(ref);
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(20000);
        connection.setRequestMethod("GET");
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IOException("截图读取失败：" + ref);
        }
        try (InputStream input = connection.getInputStream()) {
            return readAll(input);
        } finally {
            connection.disconnect();
        }
    }

    private List<String> imageReferences(String refs) {
        if (refs.startsWith("data:image")) {
            return Collections.singletonList(refs);
        }
        List<String> values = new ArrayList<>();
        Collections.addAll(values, refs.split(","));
        return values;
    }

    private String formatAnswer(PaperSubmissionAnswer answer) {
        if (SubjectType.MULTIPLE_CHOICE.getCode().equals(answer.getSubjectType())) {
            try {
                return String.join("、", JSON.parseArray(answer.getAnswerText(), String.class));
            } catch (Exception ignored) {
                return safeText(answer.getAnswerText());
            }
        }
        return safeText(answer.getAnswerText());
    }

    private void requireExportable(List<PaperSubmission> submissions) {
        if (submissions.isEmpty()) {
            throw new IllegalArgumentException("当前试卷暂无已提交用户");
        }
        if (submissions.stream().anyMatch(item -> !PaperGradingService.GRADED.equals(item.getStatus()))) {
            throw new IllegalArgumentException("仍有已提交试卷未完成判分");
        }
    }

    private void requireCompetitionFinished() {
        String status = countDownService.snapshot().getStatus();
        if (!"FINISHED".equals(status) && !"ENDED".equals(status)) {
            throw new IllegalArgumentException("比赛结束后才可以导出全部试卷");
        }
    }

    private List<Integer> submissionIds(List<PaperSubmission> submissions) {
        List<Integer> ids = new ArrayList<>();
        for (PaperSubmission submission : submissions) {
            ids.add(submission.getSubmissionId());
        }
        return ids;
    }

    private String uniquePdfName(User user, Set<String> usedNames) {
        String base = safeText(user.getUserName()).replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (base.isEmpty()) {
            base = "用户-" + user.getUserId();
        }
        String name = base + ".pdf";
        if (!usedNames.add(name.toLowerCase())) {
            name = base + "-" + user.getUserId() + ".pdf";
            usedNames.add(name.toLowerCase());
        }
        return name;
    }

    private String randomKey() {
        StringBuilder value = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            value.append(KEY_CHARS[secureRandom.nextInt(KEY_CHARS.length)]);
        }
        return value.toString();
    }

    private String archiveName(Date date) {
        return "试卷导出-" + new SimpleDateFormat("yyyyMMddHHmmss").format(date) + ".zip";
    }

    private String formatDate(Date date) {
        return date == null ? "--" : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }

    private void addMetadata(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        table.addCell(cell(label, labelFont, new java.awt.Color(239, 242, 246)));
        table.addCell(cell(value, valueFont, java.awt.Color.WHITE));
    }

    private void addSummary(PdfPTable table, String label, Integer value, Font labelFont, Font valueFont) {
        table.addCell(cell(label, labelFont, new java.awt.Color(239, 242, 246)));
        PdfPCell score = cell(safe(value) + " 分", valueFont, java.awt.Color.WHITE);
        score.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(score);
    }

    private PdfPCell cell(String text, Font font, java.awt.Color background) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(background);
        cell.setBorderColor(new java.awt.Color(214, 220, 228));
        cell.setPadding(7);
        return cell;
    }

    private Paragraph spaced(String text, Font font, float after) {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setSpacingAfter(after);
        return paragraph;
    }

    private byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int length;
        while ((length = input.read(buffer)) >= 0) {
            output.write(buffer, 0, length);
        }
        return output.toByteArray();
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }

    public static class ExportArtifact {
        private final PaperExportBatch batch;
        private final byte[] bytes;
        private final String fileName;

        ExportArtifact(PaperExportBatch batch, byte[] bytes, String fileName) {
            this.batch = batch;
            this.bytes = bytes;
            this.fileName = fileName;
        }

        public PaperExportBatch getBatch() {
            return batch;
        }

        public byte[] getBytes() {
            return bytes;
        }

        public String getFileName() {
            return fileName;
        }
    }
}
