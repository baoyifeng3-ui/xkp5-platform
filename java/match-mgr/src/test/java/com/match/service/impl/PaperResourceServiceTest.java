package com.match.service.impl;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PaperResourceServiceTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Mock
    private PaperCatalogService paperCatalogService;

    @Mock
    private SubjectManagementService subjectService;

    @Test
    public void emptyTemplateIsPresentButNotReady() throws Exception {
        Path root = temporaryFolder.newFolder("empty").toPath();
        Path dataset = Files.createDirectory(root.resolve("C"));
        Path annotations = Files.write(root.resolve("annotations.xml"),
                "<annotations><labels/></annotations>".getBytes(StandardCharsets.UTF_8));
        stubPaper("C", dataset, annotations);
        when(subjectService.countByPaper("C")).thenReturn(1);

        Map<String, Object> status = new PaperResourceService(paperCatalogService, subjectService).status("C");

        assertTrue((Boolean) status.get("annotationsPresent"));
        assertFalse((Boolean) status.get("annotationsValid"));
        assertFalse((Boolean) status.get("ready"));
    }

    @Test
    public void completeResourcesAreReady() throws Exception {
        Path root = temporaryFolder.newFolder("ready").toPath();
        Path dataset = Files.createDirectory(root.resolve("C"));
        Files.write(dataset.resolve("data.txt"), "data".getBytes(StandardCharsets.UTF_8));
        String xml = "<annotations><labels><label><name>x</name></label></labels>"
                + "<image name=\"1.jpg\"><box label=\"x\"/></image></annotations>";
        Path annotations = Files.write(root.resolve("annotations.xml"), xml.getBytes(StandardCharsets.UTF_8));
        stubPaper("C", dataset, annotations);
        when(subjectService.countByPaper("C")).thenReturn(2);

        Map<String, Object> status = new PaperResourceService(paperCatalogService, subjectService).status("C");

        assertEquals(1L, status.get("datasetFileCount"));
        assertTrue((Boolean) status.get("annotationsValid"));
        assertTrue((Boolean) status.get("ready"));
    }

    @Test
    public void paperWithQuestionsCanBeSelectedWithoutPracticalResources() throws Exception {
        Path root = temporaryFolder.newFolder("questions-only").toPath();
        Path dataset = Files.createDirectory(root.resolve("C"));
        Path annotations = Files.write(root.resolve("annotations.xml"),
                "<annotations><labels/></annotations>".getBytes(StandardCharsets.UTF_8));
        stubPaper("C", dataset, annotations);
        when(subjectService.countByPaper("C")).thenReturn(2);

        new PaperResourceService(paperCatalogService, subjectService).requireSelectable("C");
    }

    @Test
    public void validAnnotationsAllowScoringWhenDatasetDirectoryIsEmpty() throws Exception {
        Path root = temporaryFolder.newFolder("annotations-only").toPath();
        Path dataset = Files.createDirectory(root.resolve("C"));
        String xml = "<annotations><labels><label><name>x</name></label></labels>"
                + "<image name=\"1.jpg\"><box label=\"x\"/></image></annotations>";
        Path annotations = Files.write(root.resolve("annotations.xml"), xml.getBytes(StandardCharsets.UTF_8));
        stubPaper("C", dataset, annotations);
        when(subjectService.countByPaper("C")).thenReturn(1);

        new PaperResourceService(paperCatalogService, subjectService).requireReady("C");
    }

    private void stubPaper(String paper, Path dataset, Path annotations) {
        when(paperCatalogService.requireRegistered(paper)).thenReturn(paper);
        when(paperCatalogService.datasetDirectory(paper)).thenReturn(dataset);
        when(paperCatalogService.annotationsFile(paper)).thenReturn(annotations);
    }
}
