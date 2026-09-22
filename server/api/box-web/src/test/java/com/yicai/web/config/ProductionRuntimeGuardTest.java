package com.yicai.web.config;

import com.yicai.oss.constant.CloudConstant;
import com.yicai.system.service.ISysConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionRuntimeGuardTest {
    private final ISysConfigService configs = mock(ISysConfigService.class);

    @Test void acceptsMatchingReviewedAndDatabaseProvider() {
        when(configs.selectConfigByKey(CloudConstant.CLOUD_STORAGE_CONFIG_KEY)).thenReturn("minio");
        ProductionRuntimeGuard guard = new ProductionRuntimeGuard(configs,
                new MockEnvironment().withProperty("qinglife.oss.provider", "minio"));
        assertDoesNotThrow(() -> guard.run(null));
    }

    @Test void refusesDatabaseProviderDifferentFromReviewedEnvironment() {
        when(configs.selectConfigByKey(CloudConstant.CLOUD_STORAGE_CONFIG_KEY)).thenReturn("qiniu");
        ProductionRuntimeGuard guard = new ProductionRuntimeGuard(configs,
                new MockEnvironment().withProperty("qinglife.oss.provider", "minio"));
        assertThrows(IllegalStateException.class, () -> guard.run(null));
    }

    @Test void treatsMissingDatabaseSelectionAsDisabled() {
        when(configs.selectConfigByKey(CloudConstant.CLOUD_STORAGE_CONFIG_KEY)).thenReturn(null);
        ProductionRuntimeGuard guard = new ProductionRuntimeGuard(configs, new MockEnvironment());
        assertDoesNotThrow(() -> guard.run(null));
    }
}
