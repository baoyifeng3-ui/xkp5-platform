package com.match.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.match.entity.SystemSetting;
import com.match.mapper.SystemSettingMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SystemSettingService {
    public static final String ACTIVE_PAPER = "active_paper";
    public static final String PLATFORM_NAME = "platform_name";
    public static final String THEME_COLOR = "theme_color";
    public static final String LOGIN_BACKGROUND_URL = "login_background_url";
    public static final String LOGIN_BACKGROUND_OVERLAY_OPACITY = "login_background_overlay_opacity";
    public static final String PLATFORM_LOGO_URL = "platform_logo_url";
    public static final String LOGIN_ENGLISH_SUBTITLE = "login_english_subtitle";
    public static final String LOGIN_BRAND_NAME = "login_brand_name";
    public static final String LOGIN_TITLE = "login_title";
    public static final String LOGIN_DESCRIPTION = "login_description";
    public static final String LOGIN_COPYRIGHT = "login_copyright";
    public static final String COMPETITION_CONTENT = "competition_content";
    public static final String COMPETITION_HELP_CONTENT = "competition_help_content";
    public static final String DEFAULT_PLATFORM_NAME = "数据杯管理台";
    public static final String DEFAULT_THEME_COLOR = "#162d45";
    public static final String DEFAULT_LOGIN_BACKGROUND_URL = "";
    public static final String DEFAULT_PLATFORM_LOGO_URL = "";
    public static final String DEFAULT_LOGIN_ENGLISH_SUBTITLE = "XKP5.0 MANAGEMENT PLATFORM";
    public static final String DEFAULT_LOGIN_BRAND_NAME = "数智杯竞赛平台-登陆页";
    public static final String DEFAULT_LOGIN_TITLE = "进入比赛工作台";
    public static final String DEFAULT_LOGIN_DESCRIPTION = "参赛账号可在开放登录后进入平台，比赛开始前将显示赛前倒计时。";
    public static final String DEFAULT_LOGIN_COPYRIGHT = "2026 数智杯人工智能比赛平台";
    public static final int MAX_PLATFORM_NAME_LENGTH = 30;

    private final SystemSettingMapper settingMapper;
    private final PaperCatalogService paperCatalogService;

    public SystemSettingService(SystemSettingMapper settingMapper, PaperCatalogService paperCatalogService) {
        this.settingMapper = settingMapper;
        this.paperCatalogService = paperCatalogService;
    }

    public String getActivePaper() {
        SystemSetting setting = settingMapper.selectById(ACTIVE_PAPER);
        if (setting == null || setting.getSettingValue() == null) {
            return "";
        }
        String paper = setting.getSettingValue().trim().toUpperCase();
        return paper.matches("[A-Z]") ? paper : "";
    }

    public String getPlatformName() {
        SystemSetting setting = settingMapper.selectById(PLATFORM_NAME);
        if (setting == null || setting.getSettingValue() == null
                || setting.getSettingValue().trim().isEmpty()) {
            return DEFAULT_PLATFORM_NAME;
        }
        return setting.getSettingValue().trim();
    }

    public String getThemeColor() {
        String value = getValue(THEME_COLOR, DEFAULT_THEME_COLOR);
        return value.matches("#[0-9a-fA-F]{6}") ? value : DEFAULT_THEME_COLOR;
    }

    public String getLoginBackgroundUrl() {
        return getValue(LOGIN_BACKGROUND_URL, DEFAULT_LOGIN_BACKGROUND_URL);
    }

    public int getLoginBackgroundOverlayOpacity() {
        String value = getValue(LOGIN_BACKGROUND_OVERLAY_OPACITY, "35");
        try {
            int opacity = Integer.parseInt(value);
            return opacity >= 0 && opacity <= 100 ? opacity : 35;
        } catch (NumberFormatException ignored) {
            return 35;
        }
    }

    public Map<String, String> getPlatformSettings() {
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("platformName", getPlatformName());
        settings.put("themeColor", getThemeColor());
        settings.put("loginBackgroundUrl", getLoginBackgroundUrl());
        settings.put("loginBackgroundOverlayOpacity", String.valueOf(getLoginBackgroundOverlayOpacity()));
        settings.put("platformLogoUrl", getValue(PLATFORM_LOGO_URL, DEFAULT_PLATFORM_LOGO_URL));
        settings.put("loginEnglishSubtitle", getValue(LOGIN_ENGLISH_SUBTITLE, DEFAULT_LOGIN_ENGLISH_SUBTITLE));
        settings.put("loginBrandName", getValue(LOGIN_BRAND_NAME, DEFAULT_LOGIN_BRAND_NAME));
        settings.put("loginTitle", getValue(LOGIN_TITLE, DEFAULT_LOGIN_TITLE));
        settings.put("loginDescription", getValue(LOGIN_DESCRIPTION, DEFAULT_LOGIN_DESCRIPTION));
        settings.put("loginCopyright", getValue(LOGIN_COPYRIGHT, DEFAULT_LOGIN_COPYRIGHT));
        return settings;
    }

    public String getCompetitionHelpContent() {
        return getValue(COMPETITION_HELP_CONTENT, "");
    }

    @Transactional
    public String setCompetitionHelpContent(String content, Integer adminId) {
        String value = content == null ? "" : content.trim();
        if (value.length() > 100000) {
            throw new IllegalArgumentException("比赛帮助内容不能超过 100000 个字符");
        }
        saveSetting(COMPETITION_HELP_CONTENT, value, adminId);
        return value;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Map<String, String>> getCompetitionContent() {
        String value = getValue(COMPETITION_CONTENT, "{}");
        try {
            Map<String, Map<String, String>> parsed = JSON.parseObject(value,
                    new TypeReference<Map<String, Map<String, String>>>() {});
            return parsed == null ? new LinkedHashMap<>() : parsed;
        } catch (Exception exception) {
            return new LinkedHashMap<>();
        }
    }

    @Transactional
    public Map<String, Map<String, String>> setCompetitionContent(
            Map<String, com.match.dto.CompetitionContentRequest.CompetitionContentSection> sections,
            Integer adminId) {
        Map<String, Map<String, String>> normalized = new LinkedHashMap<>();
        if (sections != null) {
            sections.forEach((key, section) -> {
                if (key == null || section == null) return;
                String title = section.getTitle() == null ? "" : section.getTitle().trim();
                String body = section.getBody() == null ? "" : section.getBody().trim();
                if (title.length() > 100 || body.length() > 100000) {
                    throw new IllegalArgumentException("赛规赛程内容过长");
                }
                Map<String, String> item = new LinkedHashMap<>();
                item.put("title", title);
                item.put("body", body);
                normalized.put(key, item);
            });
        }
        saveSetting(COMPETITION_CONTENT, JSON.toJSONString(normalized), adminId);
        return normalized;
    }

    @Transactional
    public String setActivePaper(String paperType, Integer adminId) {
        String paper = paperType == null ? "" : paperType.trim().toUpperCase();
        if (!paper.isEmpty()) {
            paper = paperCatalogService.requireRegistered(paper);
        }
        saveSetting(ACTIVE_PAPER, paper, adminId);
        return paper;
    }

    @Transactional
    public String setPlatformName(String platformName, Integer adminId) {
        String name = platformName == null ? "" : platformName.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("平台名称不能为空");
        }
        if (name.length() > MAX_PLATFORM_NAME_LENGTH) {
            throw new IllegalArgumentException("平台名称不能超过 30 个字符");
        }
        saveSetting(PLATFORM_NAME, name, adminId);
        return name;
    }

    @Transactional
    public Map<String, String> setPlatformSettings(String platformName, String themeColor,
                                                   String loginBackgroundUrl, Integer adminId) {
        return setPlatformSettings(platformName, themeColor, loginBackgroundUrl,
                getValue(LOGIN_BRAND_NAME, DEFAULT_LOGIN_BRAND_NAME),
                getValue(LOGIN_TITLE, DEFAULT_LOGIN_TITLE),
                getValue(LOGIN_DESCRIPTION, DEFAULT_LOGIN_DESCRIPTION),
                getValue(LOGIN_COPYRIGHT, DEFAULT_LOGIN_COPYRIGHT), adminId);
    }

    @Transactional
    public Map<String, String> setPlatformSettings(String platformName, String themeColor,
                                                   String loginBackgroundUrl, String loginBrandName,
                                                   String loginTitle, String loginDescription,
                                                   String loginCopyright, Integer adminId) {
        return setPlatformSettings(platformName, themeColor, loginBackgroundUrl,
                getValue(PLATFORM_LOGO_URL, DEFAULT_PLATFORM_LOGO_URL),
                getValue(LOGIN_ENGLISH_SUBTITLE, DEFAULT_LOGIN_ENGLISH_SUBTITLE), loginBrandName,
                loginTitle, loginDescription, loginCopyright, adminId);
    }

    @Transactional
    public Map<String, String> setPlatformSettings(String platformName, String themeColor,
                                                   String loginBackgroundUrl, String platformLogoUrl,
                                                   String loginEnglishSubtitle, String loginBrandName,
                                                   String loginTitle, String loginDescription,
                                                   String loginCopyright, Integer adminId) {
        return setPlatformSettings(platformName, themeColor, loginBackgroundUrl, platformLogoUrl,
                loginEnglishSubtitle, loginBrandName, loginTitle, loginDescription, loginCopyright,
                getLoginBackgroundOverlayOpacity(), adminId);
    }

    @Transactional
    public Map<String, String> setPlatformSettings(String platformName, String themeColor,
                                                   String loginBackgroundUrl, String platformLogoUrl,
                                                   String loginEnglishSubtitle, String loginBrandName,
                                                   String loginTitle, String loginDescription,
                                                   String loginCopyright, Integer loginBackgroundOverlayOpacity,
                                                   Integer adminId) {
        String name = platformName == null ? "" : platformName.trim();
        if (name.isEmpty()) throw new IllegalArgumentException("平台名称不能为空");
        if (name.length() > MAX_PLATFORM_NAME_LENGTH) throw new IllegalArgumentException("平台名称不能超过 30 个字符");
        String color = themeColor == null ? DEFAULT_THEME_COLOR : themeColor.trim();
        if (!color.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("主题色必须是 6 位十六进制颜色");
        String background = loginBackgroundUrl == null ? "" : loginBackgroundUrl.trim();
        if (background.length() > 1000) throw new IllegalArgumentException("登录背景地址不能超过 1000 个字符");
        String logo = platformLogoUrl == null ? "" : platformLogoUrl.trim();
        if (logo.length() > 1000) throw new IllegalArgumentException("平台 Logo 地址不能超过 1000 个字符");
        String englishSubtitle = normalizeLoginCopy(loginEnglishSubtitle, "登录页英文副标题", 100);
        String brandName = normalizeLoginCopy(loginBrandName, "登录页顶部名称", 60);
        String title = normalizeLoginCopy(loginTitle, "登录页主标题", 60);
        String description = normalizeLoginCopy(loginDescription, "登录页说明文字", 300);
        String copyright = normalizeLoginCopy(loginCopyright, "登录页版权文字", 100);
        saveSetting(PLATFORM_NAME, name, adminId);
        saveSetting(THEME_COLOR, color.toLowerCase(), adminId);
        saveSetting(LOGIN_BACKGROUND_URL, background, adminId);
        setLoginBackgroundOverlayOpacity(loginBackgroundOverlayOpacity == null
                ? getLoginBackgroundOverlayOpacity() : loginBackgroundOverlayOpacity, adminId);
        saveSetting(PLATFORM_LOGO_URL, logo, adminId);
        saveSetting(LOGIN_ENGLISH_SUBTITLE, englishSubtitle, adminId);
        saveSetting(LOGIN_BRAND_NAME, brandName, adminId);
        saveSetting(LOGIN_TITLE, title, adminId);
        saveSetting(LOGIN_DESCRIPTION, description, adminId);
        saveSetting(LOGIN_COPYRIGHT, copyright, adminId);
        return getPlatformSettings();
    }

    @Transactional
    public int setLoginBackgroundOverlayOpacity(Integer opacity, Integer adminId) {
        if (opacity == null || opacity < 0 || opacity > 100) {
            throw new IllegalArgumentException("背景蒙版透明度必须在 0 到 100 之间");
        }
        saveSetting(LOGIN_BACKGROUND_OVERLAY_OPACITY, String.valueOf(opacity), adminId);
        return opacity;
    }

    private String normalizeLoginCopy(String value, String label, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + "不能为空");
        if (normalized.length() > maxLength) throw new IllegalArgumentException(label + "不能超过 " + maxLength + " 个字符");
        return normalized;
    }

    private String getValue(String key, String defaultValue) {
        SystemSetting setting = settingMapper.selectById(key);
        if (setting == null || setting.getSettingValue() == null) return defaultValue;
        return setting.getSettingValue().trim();
    }

    private void saveSetting(String key, String value, Integer adminId) {
        SystemSetting setting = settingMapper.selectById(key);
        if (setting == null) {
            setting = new SystemSetting();
            setting.setSettingKey(key);
            setting.setSettingValue(value);
            setting.setUpdatedBy(adminId);
            settingMapper.insert(setting);
        } else {
            setting.setSettingValue(value);
            setting.setUpdatedBy(adminId);
            settingMapper.updateById(setting);
        }
    }
}
