package com.match.registry.service;

import com.match.registry.dto.ImageReviewRequest;
import com.match.registry.dto.ImageUploadCreateRequest;
import com.match.registry.persistence.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ImageUploadServiceTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private ImageUploadMapper uploads; private ImageUploadChunkMapper chunks; private ImageArtifactMapper artifacts; private ImageUploadService service;
    @Before public void setUp() { uploads = mock(ImageUploadMapper.class); chunks = mock(ImageUploadChunkMapper.class); artifacts = mock(ImageArtifactMapper.class); service = new ImageUploadService(uploads, chunks, artifacts, temp.getRoot().toPath(), 1024, 4096, Clock.fixed(Instant.parse("2026-08-21T00:00:00Z"), ZoneOffset.UTC)); }
    private ImageUploadCreateRequest request(int total, int chunk) { ImageUploadCreateRequest r = new ImageUploadCreateRequest(); r.setOriginalFilename("image.tar"); r.setTotalSize((long) total); r.setChunkSize(chunk); r.setExpectedSha256(null); r.setVersion("v1"); r.setGroupId("g"); r.setComponentType("ANNOTATION"); return r; }
    @Test(expected = ImageUploadService.UploadException.class) public void writesRequireSuperAdmin() { service.create("ADMIN", request(1, 1), 1); }
    @Test public void acceptsOutOfOrderChunksAndIdenticalRetry() throws Exception { ImageUploadRecord u = service.create("SUPER_ADMIN", request(4, 2), 1); when(uploads.selectForUpdate(u.getUploadId())).thenReturn(u); when(chunks.selectForUpdate(anyString(), anyInt())).thenReturn(null); byte[] a = "ab".getBytes("UTF-8"); String hash = sha(a); service.putChunk("SUPER_ADMIN", u.getUploadId(), 1, new MockMultipartFile("file", a), hash); ImageUploadChunkRecord existing = new ImageUploadChunkRecord(); existing.setUploadId(u.getUploadId()); existing.setChunkIndex(1); existing.setChunkSize(2); existing.setChunkSha256(hash); existing.setStoredBytes(2L); when(chunks.selectForUpdate(u.getUploadId(), 1)).thenReturn(existing); assertEquals(hash, service.putChunk("SUPER_ADMIN", u.getUploadId(), 1, new MockMultipartFile("file", a), hash).getChunkSha256()); }
    @Test public void rejectsConflictingDuplicate() throws Exception { ImageUploadRecord u = service.create("SUPER_ADMIN", request(2, 2), 1); when(uploads.selectForUpdate(u.getUploadId())).thenReturn(u); ImageUploadChunkRecord e = new ImageUploadChunkRecord(); e.setChunkSha256("00"); e.setChunkIndex(0); e.setChunkSize(2); e.setStoredBytes(2L); when(chunks.selectForUpdate(u.getUploadId(), 0)).thenReturn(e); try { service.putChunk("SUPER_ADMIN", u.getUploadId(), 0, new MockMultipartFile("file", "ab".getBytes()), "11"); fail(); } catch (ImageUploadService.UploadException ex) { assertEquals(ImageUploadService.CODE_CONFLICT, ex.getCode()); } }
    @Test public void reviewIsIdempotent() { ImageArtifactRecord a = new ImageArtifactRecord(); a.setArtifactId("a"); a.setReviewState(ImageUploadService.APPROVED); when(artifacts.selectById("a")).thenReturn(a); ImageReviewRequest r = new ImageReviewRequest(); r.setDecision(ImageUploadService.APPROVED); assertSame(a, service.review("SUPER_ADMIN", "a", r, 1)); verify(artifacts, never()).updateById(any()); }
    private String sha(byte[] b) throws Exception { byte[] d = MessageDigest.getInstance("SHA-256").digest(b); StringBuilder s = new StringBuilder(); for (byte x : d) s.append(String.format("%02x", x)); return s.toString(); }
}
