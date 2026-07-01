package com.khankiddo.learning.controller;

import com.khankiddo.learning.config.SiteProperties;
import com.khankiddo.learning.dto.SiteInfoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/site")
@RequiredArgsConstructor
public class SiteController {

    private static final String DEFAULT_ICP_URL = "https://beian.miit.gov.cn/";
    private static final String DEFAULT_PSB_URL = "https://beian.mps.gov.cn/";

    private final SiteProperties siteProperties;

    @GetMapping
    public SiteInfoResponse siteInfo() {
        return SiteInfoResponse.builder()
                .icpNumber(trimToNull(siteProperties.getIcpNumber()))
                .icpUrl(resolveUrl(siteProperties.getIcpUrl(), DEFAULT_ICP_URL))
                .psbNumber(trimToNull(siteProperties.getPsbNumber()))
                .psbUrl(resolveUrl(siteProperties.getPsbUrl(), DEFAULT_PSB_URL))
                .build();
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static String resolveUrl(String value, String fallback) {
        if (StringUtils.hasText(value)) {
            return value.trim();
        }
        return fallback;
    }
}
