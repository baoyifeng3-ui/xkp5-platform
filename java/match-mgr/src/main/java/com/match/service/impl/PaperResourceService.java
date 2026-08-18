package com.match.service.impl;

import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PaperResourceService {
    private final PaperCatalogService paperCatalogService;
    private final SubjectManagementService subjectService;

    public PaperResourceService(PaperCatalogService paperCatalogService,
                                SubjectManagementService subjectService) {
        this.paperCatalogService = paperCatalogService;
        this.subjectService = subjectService;
    }

    public Map<String, Object> status(String paperType) {
        String paper = paperCatalogService.requireRegistered(paperType);
        Path datasetDirectory = paperCatalogService.datasetDirectory(paper);
        Path annotations = paperCatalogService.annotationsFile(paper);
        long fileCount = countFiles(datasetDirectory);
        boolean annotationsPresent = Files.isRegularFile(annotations);
        boolean annotationsValid = annotationsPresent && hasUsableAnnotations(annotations);
        int subjectCount = subjectService.countByPaper(paper);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("paperType", paper);
        result.put("datasetFileCount", fileCount);
        result.put("annotationsPresent", annotationsPresent);
        result.put("annotationsValid", annotationsValid);
        result.put("subjectCount", subjectCount);
        result.put("ready", fileCount > 0 && annotationsValid && subjectCount > 0);
        return result;
    }

    public void requireReady(String paperType) {
        Map<String, Object> resourceStatus = status(paperType);
        if (!Boolean.TRUE.equals(resourceStatus.get("ready"))) {
            if (((Integer) resourceStatus.get("subjectCount")) == 0) {
                throw new IllegalArgumentException("赛卷 " + paperCatalogService.normalize(paperType)
                        + " 暂无题目，请先在试卷题目中添加");
            }
            throw new IllegalArgumentException("赛卷 " + paperCatalogService.normalize(paperType)
                    + " 资源不完整，请检查数据目录和 annotations.xml");
        }
    }

    private long countFiles(Path directory) {
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        try (java.util.stream.Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile).count();
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取赛卷资源目录: " + directory, exception);
        }
    }

    private boolean hasUsableAnnotations(Path annotations) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(annotations.toFile());
            return document.getElementsByTagName("label").getLength() > 0
                    && document.getElementsByTagName("image").getLength() > 0;
        } catch (Exception exception) {
            return false;
        }
    }
}
