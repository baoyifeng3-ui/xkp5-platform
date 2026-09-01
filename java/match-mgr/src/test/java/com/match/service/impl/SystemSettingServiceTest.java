package com.match.service.impl;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.match.dto.CompetitionContentRequest;
import com.match.entity.SystemSetting;
import com.match.mapper.SystemSettingMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SystemSettingServiceTest {
    @Mock
    private SystemSettingMapper settingMapper;

    @Mock
    private PaperCatalogService paperCatalogService;

    private SystemSettingService service;

    @Before
    public void setUp() {
        service = new SystemSettingService(settingMapper, paperCatalogService);
    }

    @Test
    public void returnsDefaultPlatformNameWhenSettingIsMissing() {
        when(settingMapper.selectById(SystemSettingService.PLATFORM_NAME)).thenReturn(null);

        assertEquals("数据杯管理台", service.getPlatformName());
    }

    @Test
    public void returnsNavyDefaultThemeWhenSettingIsMissing() {
        when(settingMapper.selectById(SystemSettingService.THEME_COLOR)).thenReturn(null);

        assertEquals("#162d45", service.getThemeColor());
    }

    @Test
    public void returnsConfiguredLogoAndEnglishSubtitle() {
        when(settingMapper.selectById(SystemSettingService.PLATFORM_LOGO_URL))
                .thenReturn(setting(SystemSettingService.PLATFORM_LOGO_URL, " /files/logo.png "));
        when(settingMapper.selectById(SystemSettingService.LOGIN_ENGLISH_SUBTITLE))
                .thenReturn(setting(SystemSettingService.LOGIN_ENGLISH_SUBTITLE, " AI TRAINING PLATFORM "));

        Map<String, String> settings = service.getPlatformSettings();

        assertEquals("/files/logo.png", settings.get("platformLogoUrl"));
        assertEquals("AI TRAINING PLATFORM", settings.get("loginEnglishSubtitle"));
    }

    @Test
    public void usesInputStrategyForStringSettingKey() throws Exception {
        TableId tableId = SystemSetting.class.getDeclaredField("settingKey").getAnnotation(TableId.class);

        assertEquals(IdType.INPUT, tableId.type());
    }

    @Test
    public void trimsAndCreatesPlatformName() {
        when(settingMapper.selectById(SystemSettingService.PLATFORM_NAME)).thenReturn(null);

        assertEquals("赛事管理台", service.setPlatformName(" 赛事管理台 ", 9));

        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(settingMapper).insert(captor.capture());
        assertEquals(SystemSettingService.PLATFORM_NAME, captor.getValue().getSettingKey());
        assertEquals("赛事管理台", captor.getValue().getSettingValue());
        assertEquals(Integer.valueOf(9), captor.getValue().getUpdatedBy());
    }

    @Test
    public void updatesExistingPlatformName() {
        SystemSetting setting = new SystemSetting();
        setting.setSettingKey(SystemSettingService.PLATFORM_NAME);
        setting.setSettingValue("旧名称");
        when(settingMapper.selectById(SystemSettingService.PLATFORM_NAME)).thenReturn(setting);

        assertEquals("新名称", service.setPlatformName("新名称", 3));

        verify(settingMapper).updateById(setting);
        assertEquals("新名称", setting.getSettingValue());
        assertEquals(Integer.valueOf(3), setting.getUpdatedBy());
    }

    @Test
    public void rejectsBlankPlatformName() {
        assertInvalidPlatformName("   ", "不能为空");
    }

    @Test
    public void rejectsPlatformNameLongerThanThirtyCharacters() {
        assertInvalidPlatformName("1234567890123456789012345678901", "不能超过 30 个字符");
    }

    @Test
    public void savesPlatformSettingsWithEmptyLoginBackground() {
        SystemSetting name = setting(SystemSettingService.PLATFORM_NAME, "旧平台");
        SystemSetting theme = setting(SystemSettingService.THEME_COLOR, "#123456");
        SystemSetting background = setting(SystemSettingService.LOGIN_BACKGROUND_URL, "https://example.com/old.jpg");
        when(settingMapper.selectById(SystemSettingService.PLATFORM_NAME)).thenReturn(name);
        when(settingMapper.selectById(SystemSettingService.THEME_COLOR)).thenReturn(theme);
        when(settingMapper.selectById(SystemSettingService.LOGIN_BACKGROUND_URL)).thenReturn(background);

        Map<String, String> saved = service.setPlatformSettings(" 新平台 ", "#162D45", null, 5);

        assertEquals("新平台", saved.get("platformName"));
        assertEquals("#162d45", saved.get("themeColor"));
        assertEquals("", saved.get("loginBackgroundUrl"));
        assertEquals("新平台", name.getSettingValue());
        assertEquals("#162d45", theme.getSettingValue());
        assertEquals("", background.getSettingValue());
        verify(settingMapper).updateById(name);
        verify(settingMapper).updateById(theme);
        verify(settingMapper).updateById(background);
    }

    @Test
    public void rejectsInvalidThemeColorWithoutSavingPlatformSettings() {
        try {
            service.setPlatformSettings("数据杯", "yellow", "", 5);
            fail("应拒绝无效的主题色");
        } catch (IllegalArgumentException exception) {
            assertTrue(exception.getMessage().contains("6 位十六进制颜色"));
        }
        verify(settingMapper, never()).insert(org.mockito.ArgumentMatchers.any(SystemSetting.class));
        verify(settingMapper, never()).updateById(org.mockito.ArgumentMatchers.any(SystemSetting.class));
    }

    @Test
    public void savesAndReturnsCompetitionContent() {
        when(settingMapper.selectById(SystemSettingService.COMPETITION_CONTENT)).thenReturn(null);
        CompetitionContentRequest.CompetitionContentSection section =
                new CompetitionContentRequest.CompetitionContentSection();
        section.setTitle(" 竞赛规则 ");
        section.setBody(" 第一条\n第二条 ");
        Map<String, CompetitionContentRequest.CompetitionContentSection> sections = new LinkedHashMap<>();
        sections.put("matchRule", section);

        Map<String, Map<String, String>> saved = service.setCompetitionContent(sections, 7);

        assertEquals("竞赛规则", saved.get("matchRule").get("title"));
        assertEquals("第一条\n第二条", saved.get("matchRule").get("body"));
        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(settingMapper).insert(captor.capture());
        assertEquals(SystemSettingService.COMPETITION_CONTENT, captor.getValue().getSettingKey());
    }

    @Test
    public void savesCompetitionHelpContent() {
        when(settingMapper.selectById(SystemSettingService.COMPETITION_HELP_CONTENT)).thenReturn(null);

        assertEquals("第一行\n第二行", service.setCompetitionHelpContent(" 第一行\n第二行 ", 8));

        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(settingMapper).insert(captor.capture());
        assertEquals(SystemSettingService.COMPETITION_HELP_CONTENT, captor.getValue().getSettingKey());
        assertEquals("第一行\n第二行", captor.getValue().getSettingValue());
    }

    @Test
    public void savesRegisteredDynamicPaper() {
        when(paperCatalogService.requireRegistered("C")).thenReturn("C");
        when(settingMapper.selectById(SystemSettingService.ACTIVE_PAPER)).thenReturn(null);

        assertEquals("C", service.setActivePaper(" c ", 4));

        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(settingMapper).insert(captor.capture());
        assertEquals("C", captor.getValue().getSettingValue());
        verify(paperCatalogService).requireRegistered("C");
    }

    private void assertInvalidPlatformName(String value, String message) {
        try {
            service.setPlatformName(value, 1);
            fail("应拒绝无效的平台名称");
        } catch (IllegalArgumentException exception) {
            assertTrue(exception.getMessage().contains(message));
        }
        verify(settingMapper, never()).insert(org.mockito.ArgumentMatchers.any(SystemSetting.class));
        verify(settingMapper, never()).updateById(org.mockito.ArgumentMatchers.any(SystemSetting.class));
    }

    private SystemSetting setting(String key, String value) {
        SystemSetting setting = new SystemSetting();
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        return setting;
    }
}
