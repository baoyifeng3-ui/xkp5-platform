package com.match.registry.service;

import com.match.registry.persistence.ImageArtifactMapper;
import com.match.registry.persistence.ImageArtifactRecord;
import com.match.registry.persistence.ImageUploadMapper;
import com.match.registry.persistence.ImageUploadRecord;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ImageImportWorkerTest {
    private static final Instant NOW = Instant.parse("2026-08-21T08:09:10Z");
    private static final String ARTIFACT_ID = "artifact-1";
    private static final String UPLOAD_ID = "upload-1";
    private static final String DIGEST = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private ImageArtifactMapper artifacts;
    private ImageUploadMapper uploads;
    private RegistryImportTool tool;
    private Path archive;

    @Before
    public void setUp() throws Exception {
        artifacts = mock(ImageArtifactMapper.class);
        uploads = mock(ImageUploadMapper.class);
        tool = mock(RegistryImportTool.class);
        archive = temp.getRoot().toPath().resolve(UPLOAD_ID).resolve("archive");
        Files.createDirectories(archive.getParent());
        Files.write(archive, dockerArchive());
        when(uploads.selectByArtifactId(ARTIFACT_ID)).thenReturn(upload());
    }

    @Test
    public void importsApprovedArtifactAndRetainsArchiveForAgentDeployment() throws Exception {
        when(artifacts.selectById(ARTIFACT_ID)).thenReturn(approved());
        successfulClaim();
        when(tool.importArchive(any(RegistryImportTool.ImportRequest.class), any(RegistryImportTool.ProgressListener.class)))
                .thenReturn(new RegistryImportTool.ImportResult(DIGEST, "copied"));
        when(artifacts.completeImport(eq(ARTIFACT_ID), anyString(), eq(DIGEST), any(LocalDateTime.class))).thenAnswer(invocation -> {
            assertTrue("archive must exist until digest persistence succeeds", Files.exists(archive));
            return 1;
        });

        assertTrue(worker().process(ARTIFACT_ID));

        verify(artifacts).claimImport(eq(ARTIFACT_ID), anyString(), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(artifacts).completeImport(eq(ARTIFACT_ID), anyString(), eq(DIGEST), any(LocalDateTime.class));
        assertTrue(Files.exists(archive));
    }

    @Test
    public void ignoresArtifactsThatAreNotApproved() {
        ImageArtifactRecord pending = approved();
        pending.setReviewState("PENDING_REVIEW");
        when(artifacts.selectById(ARTIFACT_ID)).thenReturn(pending);

        assertFalse(worker().process(ARTIFACT_ID));

        verify(artifacts, never()).claimImport(eq(ARTIFACT_ID), anyString(), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(tool, never()).importArchive(any(RegistryImportTool.ImportRequest.class), any(RegistryImportTool.ProgressListener.class));
    }

    @Test
    public void atomicClaimAllowsOnlyOneImportAttemptPerArtifact() {
        when(artifacts.selectById(ARTIFACT_ID)).thenReturn(approved());
        when(artifacts.claimImport(eq(ARTIFACT_ID), anyString(), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(0);

        assertFalse(worker().process(ARTIFACT_ID));

        verify(tool, never()).importArchive(any(RegistryImportTool.ImportRequest.class), any(RegistryImportTool.ProgressListener.class));
    }

    @Test
    public void toolFailureIsRecordedAndStagingIsRetainedForRetry() {
        when(artifacts.selectById(ARTIFACT_ID)).thenReturn(approved());
        successfulClaim();
        when(tool.importArchive(any(RegistryImportTool.ImportRequest.class), any(RegistryImportTool.ProgressListener.class)))
                .thenThrow(new RegistryImportTool.ImportException("TOOL_FAILED", "copy failed"));

        assertFalse(worker().process(ARTIFACT_ID));

        verify(artifacts).failImport(eq(ARTIFACT_ID), anyString(), eq("TOOL_FAILED"), eq("copy failed"),
                any(LocalDateTime.class));
        verify(artifacts, never()).completeImport(eq(ARTIFACT_ID), anyString(), any(String.class),
                any(LocalDateTime.class));
        assertTrue(Files.exists(archive));
    }

    @Test
    public void failedArtifactCanBeClaimedAndRetried() throws Exception {
        ImageArtifactRecord failed = approved();
        failed.setImportState("FAILED");
        when(artifacts.selectById(ARTIFACT_ID)).thenReturn(failed);
        successfulClaim();
        when(tool.importArchive(any(RegistryImportTool.ImportRequest.class), any(RegistryImportTool.ProgressListener.class)))
                .thenReturn(new RegistryImportTool.ImportResult(DIGEST, "copied"));
        when(artifacts.completeImport(eq(ARTIFACT_ID), anyString(), eq(DIGEST), any(LocalDateTime.class))).thenReturn(1);

        assertTrue(worker().process(ARTIFACT_ID));

        verify(tool).importArchive(any(RegistryImportTool.ImportRequest.class), any(RegistryImportTool.ProgressListener.class));
    }

    @Test
    public void readyArtifactReprocessingIsIdempotentAndRetainsPersistedArchive() {
        ImageArtifactRecord ready = approved();
        ready.setImportState("READY");
        ready.setRegistryDigest(DIGEST);
        when(artifacts.selectById(ARTIFACT_ID)).thenReturn(ready);

        assertTrue(worker().process(ARTIFACT_ID));

        verify(artifacts, never()).claimImport(eq(ARTIFACT_ID), anyString(), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(tool, never()).importArchive(any(RegistryImportTool.ImportRequest.class), any(RegistryImportTool.ProgressListener.class));
        assertTrue(Files.exists(archive));
    }

    @Test
    public void invalidToolDigestFailsWithoutPersistingOrDeletingStaging() throws Exception {
        when(artifacts.selectById(ARTIFACT_ID)).thenReturn(approved());
        successfulClaim();
        when(tool.importArchive(any(RegistryImportTool.ImportRequest.class), any(RegistryImportTool.ProgressListener.class)))
                .thenReturn(new RegistryImportTool.ImportResult("SHA256:not-immutable", "copied"));

        assertFalse(worker().process(ARTIFACT_ID));

        verify(artifacts, never()).completeImport(eq(ARTIFACT_ID), anyString(), any(String.class),
                any(LocalDateTime.class));
        verify(artifacts).failImport(eq(ARTIFACT_ID), anyString(), eq("INVALID_REGISTRY_DIGEST"),
                any(String.class), any(LocalDateTime.class));
        assertTrue(Files.exists(archive));
    }

    @Test
    public void persistenceFailureRetainsStagingAndRecordsFailure() throws Exception {
        when(artifacts.selectById(ARTIFACT_ID)).thenReturn(approved());
        successfulClaim();
        when(tool.importArchive(any(RegistryImportTool.ImportRequest.class), any(RegistryImportTool.ProgressListener.class)))
                .thenReturn(new RegistryImportTool.ImportResult(DIGEST, "copied"));
        when(artifacts.completeImport(eq(ARTIFACT_ID), anyString(), eq(DIGEST), any(LocalDateTime.class))).thenReturn(0);

        assertFalse(worker().process(ARTIFACT_ID));

        verify(artifacts).failImport(eq(ARTIFACT_ID), anyString(), eq("DIGEST_PERSIST_FAILED"),
                any(String.class), any(LocalDateTime.class));
        assertTrue(Files.exists(archive));
    }

    @Test
    public void staleImportLeaseCanBeReclaimedAfterWorkerCrash() throws Exception {
        ImageArtifactRecord stale = approved();
        stale.setImportState("IMPORTING");
        stale.setImportAttemptToken("old-attempt");
        stale.setImportLeaseExpiresAt(LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
        when(artifacts.selectById(ARTIFACT_ID)).thenReturn(stale);
        successfulClaim();
        when(tool.importArchive(any(RegistryImportTool.ImportRequest.class), any(RegistryImportTool.ProgressListener.class)))
                .thenReturn(new RegistryImportTool.ImportResult(DIGEST, "copied"));
        when(artifacts.completeImport(eq(ARTIFACT_ID), anyString(), eq(DIGEST), any(LocalDateTime.class))).thenReturn(1);

        assertTrue(worker().process(ARTIFACT_ID));

        verify(artifacts).claimImport(eq(ARTIFACT_ID), anyString(),
                eq(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)),
                eq(LocalDateTime.ofInstant(NOW.plusSeconds(900), ZoneOffset.UTC)));
    }

    @Test
    public void lateResultFromExpiredAttemptCannotOverwriteNewAttempt() throws Exception {
        when(artifacts.selectById(ARTIFACT_ID)).thenReturn(approved());
        successfulClaim();
        when(tool.importArchive(any(RegistryImportTool.ImportRequest.class), any(RegistryImportTool.ProgressListener.class)))
                .thenReturn(new RegistryImportTool.ImportResult(DIGEST, "copied"));
        when(artifacts.completeImport(eq(ARTIFACT_ID), anyString(), eq(DIGEST), any(LocalDateTime.class))).thenReturn(0);
        when(artifacts.failImport(eq(ARTIFACT_ID), anyString(), anyString(), anyString(),
                any(LocalDateTime.class))).thenReturn(0);

        assertFalse(worker().process(ARTIFACT_ID));

        assertTrue(Files.exists(archive));
    }

    private ImageImportWorker worker() {
        return new ImageImportWorker(artifacts, uploads, tool, temp.getRoot().toPath(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void successfulClaim() {
        when(artifacts.claimImport(eq(ARTIFACT_ID), anyString(), any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(1);
    }

    private ImageArtifactRecord approved() {
        ImageArtifactRecord result = new ImageArtifactRecord();
        result.setArtifactId(ARTIFACT_ID);
        result.setReviewState("APPROVED");
        result.setImportState("NOT_IMPORTED");
        result.setImageRepository("courses/vision");
        result.setImageTag("1.0.0");
        return result;
    }

    private ImageUploadRecord upload() {
        ImageUploadRecord result = new ImageUploadRecord();
        result.setUploadId(UPLOAD_ID);
        result.setArtifactId(ARTIFACT_ID);
        result.setState("PENDING_REVIEW");
        return result;
    }

    private byte[] dockerArchive() throws Exception {
        byte[] content = "[{\"Layers\":[\"a/layer.tar\",\"b/layer.tar\"]}]"
                .getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] header = new byte[512];
        byte[] name = "manifest.json".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(name, 0, header, 0, name.length);
        writeOctal(header, 124, 12, content.length);
        for (int index = 148; index < 156; index++) header[index] = ' ';
        header[156] = '0';
        long checksum = 0;
        for (byte value : header) checksum += value & 0xff;
        writeOctal(header, 148, 8, checksum);
        output.write(header);
        output.write(content);
        output.write(new byte[(512 - content.length % 512) % 512]);
        output.write(new byte[1024]);
        return output.toByteArray();
    }

    private void writeOctal(byte[] target, int offset, int length, long value) {
        String octal = Long.toOctalString(value);
        int start = offset + length - octal.length() - 1;
        for (int index = offset; index < start; index++) target[index] = '0';
        byte[] bytes = octal.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, target, start, bytes.length);
        target[offset + length - 1] = 0;
    }
}
