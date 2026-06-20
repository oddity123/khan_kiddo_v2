package com.khankiddo.learning.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 网站备案等站点信息（页脚展示）
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.site")
public class SiteProperties {

    /**
     * ICP 备案号，如：鄂ICP备2026025924号
     */
    private String icpNumber;

    /**
     * 工信部备案查询链接
     */
    private String icpUrl = "https://beian.miit.gov.cn/";

    /**
     * 公安备案号，如：鄂公网安备42010202002911号
     */
    private String psbNumber;

    /**
     * 公安备案查询链接
     */
    private String psbUrl = "https://beian.mps.gov.cn/";
}
