package com.yicai.web.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Refuse to start a production process with the repository's example credentials. */
@Component
@Profile("prod")
public class ProductionConfigurationGuard implements InitializingBean {
    private final Environment environment;

    public ProductionConfigurationGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        String secret = required("token.secret");
        if (secret.length() < 32 || secret.contains("change-me") || secret.contains("${")) {
            throw new IllegalStateException("Production requires a non-example JWT secret of at least 32 characters");
        }
        required("spring.datasource.dynamic.datasource.master.password");
        String imagePath = required("ruoyi.imagePath");
        if (!imagePath.startsWith("https://")) throw new IllegalStateException("Production public image URL must use HTTPS");
        String postLimit = required("server.undertow.max-http-post-size");
        if ("-1".equals(postLimit) || "0".equals(postLimit)) throw new IllegalStateException("Production HTTP request size must be bounded");
        required("spring.servlet.multipart.max-file-size");
        required("spring.servlet.multipart.max-request-size");
        if (environment.getProperty("qinglife.wechat.enabled", Boolean.class, false)) {
            required("qinglife.wechat.app-id");
            required("qinglife.wechat.app-secret");
        }
        String exposed = environment.getProperty("management.endpoints.web.exposure.include", "health");
        if (!"health".equalsIgnoreCase(exposed.replace(" ", "").trim())) {
            throw new IllegalStateException("Production management HTTP exposure must be limited to health");
        }
    }

    private String required(String key) {
        String value = environment.getProperty(key);
        if (value == null || value.trim().isEmpty() || value.contains("${")) {
            // Never include the value in startup logs.
            throw new IllegalStateException("Missing production configuration: " + key);
        }
        return value.trim();
    }
}
