package com.match.service.impl;

import com.match.entity.PaperDefinition;
import com.match.mapper.PaperDefinitionMapper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PaperCatalogServiceTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Mock
    private PaperDefinitionMapper paperMapper;

    @Test
    public void createsNormalizedPaperDirectoriesTemplateAndRecord() throws Exception {
        Path root = temporaryFolder.newFolder("resources").toPath();
        PaperCatalogService service = service(root);
        when(paperMapper.selectById("C")).thenReturn(null);
        when(paperMapper.insert(any(PaperDefinition.class))).thenReturn(1);

        assertEquals("C", service.create(" c ", 9));

        Path annotations = root.resolve("python/c/annotations.xml");
        assertTrue(Files.isRegularFile(annotations));
        assertTrue(new String(Files.readAllBytes(annotations), StandardCharsets.UTF_8).contains("<labels/>"));
        assertTrue(Files.isDirectory(root.resolve("dataset/C")));
        ArgumentCaptor<PaperDefinition> captor = ArgumentCaptor.forClass(PaperDefinition.class);
        verify(paperMapper).insert(captor.capture());
        assertEquals("C", captor.getValue().getPaperType());
        assertEquals(Integer.valueOf(9), captor.getValue().getCreatedBy());
    }

    @Test
    public void rejectsInvalidPaperName() throws Exception {
        PaperCatalogService service = service(temporaryFolder.newFolder("invalid").toPath());

        try {
            service.create("CC", 1);
            fail("应拒绝多个字母的卷名");
        } catch (IllegalArgumentException exception) {
            assertTrue(exception.getMessage().contains("单个英文字母"));
        }
    }

    @Test
    public void rollsBackDirectoriesWhenDatabaseInsertFails() throws Exception {
        Path root = temporaryFolder.newFolder("rollback").toPath();
        PaperCatalogService service = service(root);
        when(paperMapper.selectById("C")).thenReturn(null);
        when(paperMapper.insert(any(PaperDefinition.class))).thenThrow(new IllegalStateException("database failed"));

        try {
            service.create("C", 1);
            fail("数据库失败时应抛出异常");
        } catch (IllegalStateException exception) {
            assertEquals("database failed", exception.getMessage());
        }

        assertFalse(Files.exists(root.resolve("python/c")));
        assertFalse(Files.exists(root.resolve("dataset/C")));
    }

    private PaperCatalogService service(Path root) {
        return new PaperCatalogService(paperMapper,
                root.resolve("dataset").toString(), root.resolve("python").toString());
    }
}
