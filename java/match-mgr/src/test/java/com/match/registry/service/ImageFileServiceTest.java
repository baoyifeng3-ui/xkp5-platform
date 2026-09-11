package com.match.registry.service;

import com.match.registry.persistence.ImageFileMapper;
import com.match.registry.persistence.ImageFileRecord;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ImageFileServiceTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void completedUploadReplacesCanonicalRepositoryFilename() throws Exception {
        ImageFileMapper mapper = mock(ImageFileMapper.class);
        ImageFileRecord existing = new ImageFileRecord(); existing.setFileId("old");
        existing.setImageRepository("xkp/anno"); existing.setImageTag("v1");
        when(mapper.selectByFilename("image.tar")).thenReturn(existing);
        ImageFileService service = new ImageFileService(mapper, temp.getRoot().toPath(),
                Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC));
        Path first = temp.newFile("first.tar").toPath(); Files.write(first, new byte[]{1});
        Path second = temp.newFile("second.tar").toPath(); Files.write(second, new byte[]{2, 3});

        ImageFileRecord result = service.enable("xkp/anno", "image.tar", "v1", first, 7);
        Path canonical = temp.getRoot().toPath().resolve("files/xkp/anno/image.tar");
        assertSame(existing, result); assertArrayEquals(new byte[]{1}, Files.readAllBytes(canonical));
        service.enable("different/repository", "image.tar", "latest", second, 7);
        assertEquals("xkp/anno", existing.getImageRepository()); assertEquals("v1", existing.getImageTag());
        assertArrayEquals(new byte[]{2, 3}, Files.readAllBytes(canonical));
        verify(mapper, times(2)).updateById(existing);
        verify(mapper, never()).insert(any());
    }

    @Test public void rejectsPathsAndNonTarFiles() throws Exception {
        ImageFileService service = new ImageFileService(mock(ImageFileMapper.class), temp.getRoot().toPath(), Clock.systemUTC());
        Path source = temp.newFile("source.tar").toPath();
        try { service.enable("../bad", "image.tar", "v1", source, 1); fail(); } catch (IllegalArgumentException expected) { }
        try { service.enable("xkp/anno", "image.zip", "v1", source, 1); fail(); } catch (IllegalArgumentException expected) { }
    }

    @Test public void rebuildsMissingIndexFromCanonicalFiles() throws Exception {
        ImageFileMapper mapper=mock(ImageFileMapper.class); Path archive=temp.getRoot().toPath().resolve("files/xkp/anno/image.tar");
        Files.createDirectories(archive.getParent()); Files.write(archive,new byte[]{1});
        new ImageFileService(mapper,temp.getRoot().toPath(),Clock.systemUTC()).rebuildIndex();
        verify(mapper).insert(argThat(file -> "xkp/anno".equals(file.getImageRepository()) && "image.tar".equals(file.getOriginalFilename())));
    }

    @Test public void derivedRepositoriesDistinguishCaseAndPunctuationAndSupportChinese() {
        java.util.Set<String> repositories = new java.util.HashSet<>();
        for (String filename : new String[]{"Foo.tar", "foo.tar", "foo.bar.tar", "foo-bar.tar", "镜像.tar"}) {
            String repository = ImageFileService.repositoryFromFilename(filename);
            assertTrue(repository.matches("[a-z0-9]+(?:[._/-][a-z0-9]+)*"));
            assertEquals(repository, ImageFileService.repositoryFromFilename(filename));
            assertTrue(repositories.add(repository));
        }
    }

    @Test public void rebuiltAutomaticRepositoryKeepsLatestTag() throws Exception {
        ImageFileMapper mapper = mock(ImageFileMapper.class);
        Path archive = temp.getRoot().toPath().resolve("files").resolve(ImageFileService.repositoryFromFilename("镜像.tar")).resolve("镜像.tar");
        Files.createDirectories(archive.getParent()); Files.write(archive, new byte[]{1});
        new ImageFileService(mapper, temp.getRoot().toPath(), Clock.systemUTC()).rebuildIndex();
        verify(mapper).insert(argThat(file -> "latest".equals(file.getImageTag())));
    }

    @Test public void referencedTemplatePreventsDeletingArchiveAndRecord() throws Exception {
        ImageFileMapper mapper = mock(ImageFileMapper.class);
        ImageFileService service = new ImageFileService(mapper, temp.getRoot().toPath(), Clock.systemUTC());
        Path source = temp.newFile("source.tar").toPath(); Files.write(source, new byte[]{1});
        ImageFileRecord file = service.enable("xkp/anno", "image.tar", "latest", source, 1);
        when(mapper.selectById(file.getFileId())).thenReturn(file);
        when(mapper.countTemplateReferences("xkp/anno:latest")).thenReturn(1);
        try { service.delete(file.getFileId()); fail(); } catch (IllegalArgumentException expected) { }
        assertTrue(Files.exists(service.archive(file)));
        verify(mapper, never()).deleteById(anyString());
    }
}
