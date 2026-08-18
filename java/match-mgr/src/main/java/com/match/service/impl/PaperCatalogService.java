package com.match.service.impl;

import com.match.entity.PaperDefinition;
import com.match.mapper.PaperDefinitionMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class PaperCatalogService {
    private static final String EMPTY_ANNOTATIONS =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<annotations>\n  <labels/>\n</annotations>\n";

    private final PaperDefinitionMapper paperMapper;
    private final Path datasetRoot;
    private final Path annotationsRoot;

    public PaperCatalogService(
            PaperDefinitionMapper paperMapper,
            @Value("${match.dataset-root}") String datasetRoot,
            @Value("${match.scoring.annotations-root}") String annotationsRoot) {
        this.paperMapper = paperMapper;
        this.datasetRoot = Paths.get(datasetRoot).toAbsolutePath().normalize();
        this.annotationsRoot = Paths.get(annotationsRoot).toAbsolutePath().normalize();
    }

    public List<String> list() {
        List<PaperDefinition> definitions = paperMapper.selectList(null);
        List<String> papers = new ArrayList<>();
        if (definitions != null) {
            for (PaperDefinition definition : definitions) {
                if (definition != null && definition.getPaperType() != null) {
                    papers.add(normalize(definition.getPaperType()));
                }
            }
        }
        Collections.sort(papers);
        return papers;
    }

    public String requireRegistered(String paperType) {
        String paper = normalize(paperType);
        if (paperMapper.selectById(paper) == null) {
            throw new IllegalArgumentException("试卷 " + paper + " 尚未登记");
        }
        return paper;
    }

    public String create(String paperName, Integer adminId) {
        String paper = normalize(paperName);
        if (paperMapper.selectById(paper) != null) {
            throw new IllegalArgumentException("试卷 " + paper + " 已存在");
        }

        Path annotationsDirectory = annotationsDirectory(paper);
        Path annotationsFile = annotationsDirectory.resolve("annotations.xml");
        Path datasetDirectory = datasetDirectory(paper);
        if (Files.exists(annotationsDirectory) || Files.exists(datasetDirectory)) {
            throw new IllegalArgumentException("试卷 " + paper + " 的资源目录已存在，请联系运维人员处理");
        }

        boolean annotationsDirectoryCreated = false;
        boolean annotationsFileCreated = false;
        boolean datasetDirectoryCreated = false;
        try {
            Files.createDirectories(annotationsRoot);
            Files.createDirectories(datasetRoot);
            Files.createDirectory(annotationsDirectory);
            annotationsDirectoryCreated = true;
            Files.write(annotationsFile, EMPTY_ANNOTATIONS.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE_NEW);
            annotationsFileCreated = true;
            Files.createDirectory(datasetDirectory);
            datasetDirectoryCreated = true;

            PaperDefinition definition = new PaperDefinition();
            definition.setPaperType(paper);
            definition.setCreatedBy(adminId);
            if (paperMapper.insert(definition) != 1) {
                throw new IllegalStateException("试卷登记失败");
            }
            return paper;
        } catch (Exception exception) {
            cleanupCreatedResources(annotationsFile, annotationsDirectory, datasetDirectory,
                    annotationsFileCreated, annotationsDirectoryCreated, datasetDirectoryCreated, exception);
            if (exception instanceof RuntimeException) {
                throw (RuntimeException) exception;
            }
            throw new IllegalStateException("无法创建试卷资源目录", exception);
        }
    }

    public String normalize(String paperType) {
        String paper = paperType == null ? "" : paperType.trim().toUpperCase();
        if (!paper.matches("[A-Z]")) {
            throw new IllegalArgumentException("卷名只能是单个英文字母");
        }
        return paper;
    }

    public Path datasetDirectory(String paperType) {
        return datasetRoot.resolve(normalize(paperType)).normalize();
    }

    public Path annotationsFile(String paperType) {
        return annotationsDirectory(paperType).resolve("annotations.xml").normalize();
    }

    private Path annotationsDirectory(String paperType) {
        return annotationsRoot.resolve(normalize(paperType).toLowerCase()).normalize();
    }

    private void cleanupCreatedResources(Path annotationsFile, Path annotationsDirectory, Path datasetDirectory,
                                         boolean annotationsFileCreated, boolean annotationsDirectoryCreated,
                                         boolean datasetDirectoryCreated, Exception original) {
        try {
            if (datasetDirectoryCreated) Files.deleteIfExists(datasetDirectory);
            if (annotationsFileCreated) Files.deleteIfExists(annotationsFile);
            if (annotationsDirectoryCreated) Files.deleteIfExists(annotationsDirectory);
        } catch (IOException cleanupException) {
            original.addSuppressed(cleanupException);
        }
    }
}
