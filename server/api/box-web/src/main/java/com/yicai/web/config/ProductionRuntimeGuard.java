package com.yicai.web.config;

import com.yicai.oss.constant.CloudConstant;
import com.yicai.system.service.ISysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Verify that the database-selected OSS implementation matches the reviewed production configuration. */
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProductionRuntimeGuard implements ApplicationRunner {
    private final ISysConfigService configService;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        String configured = environment.getProperty("qinglife.oss.provider", "disabled").trim().toLowerCase();
        String selected = configService.selectConfigByKey(CloudConstant.CLOUD_STORAGE_CONFIG_KEY);
        selected = selected == null || selected.trim().isEmpty() ? "disabled" : selected.trim().toLowerCase();
        if (!configured.equals(selected)) {
            throw new IllegalStateException("Production OSS provider does not match the database selection");
        }
    }
}
