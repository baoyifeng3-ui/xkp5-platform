package com.match.dto;

import lombok.Data;

@Data
public class PlatformSettingsRequest {
    private String platformName;
    private String themeColor;
    private String loginBackgroundUrl;
    private String platformLogoUrl;
    private String loginEnglishSubtitle;
    private String loginBrandName;
    private String loginTitle;
    private String loginDescription;
    private String loginCopyright;
}
