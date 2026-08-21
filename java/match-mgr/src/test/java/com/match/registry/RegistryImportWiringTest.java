package com.match.registry;

import com.match.Application;
import com.match.registry.service.ImageImportWorker;
import com.match.registry.service.RegistryImportTool;
import org.junit.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RegistryImportWiringTest {
    @Test
    public void applicationScansRegistryPersistenceMappers() {
        MapperScan scan = Application.class.getAnnotation(MapperScan.class);
        assertNotNull(scan);
        assertTrue(Arrays.asList(scan.value()).contains("com.match.registry.persistence"));
    }

    @Test
    public void importerBeansRequireExplicitImporterEnablement() {
        assertImporterOnly(ImageImportWorker.class);
        assertImporterOnly(RegistryImportTool.class);
    }

    private void assertImporterOnly(Class<?> type) {
        ConditionalOnProperty condition = type.getAnnotation(ConditionalOnProperty.class);
        assertNotNull(type.getSimpleName() + " must be conditionally wired", condition);
        assertEquals("xkp.registry.import", condition.prefix());
        assertTrue(Arrays.asList(condition.name()).contains("enabled"));
        assertEquals("true", condition.havingValue());
        assertFalse(condition.matchIfMissing());
    }
}
