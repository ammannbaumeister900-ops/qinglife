package com.yicai.web.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class ProductionConfigurationGuardTest {
    private MockEnvironment valid() {
        return new MockEnvironment().withProperty("token.secret", "synthetic-test-secret-0123456789abcdef")
            .withProperty("spring.datasource.dynamic.datasource.master.password", "synthetic-db-password")
            .withProperty("ruoyi.imagePath", "https://static.example.test/")
            .withProperty("server.undertow.max-http-post-size", "20MB")
            .withProperty("spring.servlet.multipart.max-file-size", "10MB")
            .withProperty("spring.servlet.multipart.max-request-size", "20MB")
            .withProperty("management.endpoints.web.exposure.include", "health")
            .withProperty("token.expireTime", "480")
            .withProperty("qinglife.app-token.expire-minutes", "1440")
            .withProperty("spring.redis.host", "127.0.0.1")
            .withProperty("spring.redis.password", "synthetic-redis-password")
            .withProperty("spring.redis.ssl", "false")
            .withProperty("qinglife.redis.allow-insecure-internal", "true")
            .withProperty("qinglife.oss.provider", "disabled");
    }
    @Test void acceptsExplicitProductionSettings() {
        assertDoesNotThrow(() -> new ProductionConfigurationGuard(valid()).afterPropertiesSet());
    }
    @Test void refusesExampleSecret() {
        assertThrows(IllegalStateException.class, () -> new ProductionConfigurationGuard(
            valid().withProperty("token.secret", "change-me-before-running")).afterPropertiesSet());
    }
    @Test void refusesBlankDatabasePasswordWithoutLoggingIt() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> new ProductionConfigurationGuard(
            valid().withProperty("spring.datasource.dynamic.datasource.master.password", " ")).afterPropertiesSet());
        assertFalse(error.getMessage().contains("synthetic-test-secret"));
    }
    @Test void refusesWildcardActuatorExposure() {
        assertThrows(IllegalStateException.class, () -> new ProductionConfigurationGuard(
            valid().withProperty("management.endpoints.web.exposure.include", "*")).afterPropertiesSet());
    }
    @Test void requiresWechatCredentialsWhenEnabled() {
        assertThrows(IllegalStateException.class, () -> new ProductionConfigurationGuard(
            valid().withProperty("qinglife.wechat.enabled", "true")).afterPropertiesSet());
    }
    @Test void refusesHttpPublicImages() {
        assertThrows(IllegalStateException.class, () -> new ProductionConfigurationGuard(
            valid().withProperty("ruoyi.imagePath", "http://static.example.test/")).afterPropertiesSet());
    }
    @Test void refusesUnlimitedUnitsAndUploads() {
        for(String value:new String[]{"-1MB","0B","invalid"}) {
            assertThrows(IllegalStateException.class,()->new ProductionConfigurationGuard(valid().withProperty("server.undertow.max-http-post-size",value)).afterPropertiesSet());
            assertThrows(IllegalStateException.class,()->new ProductionConfigurationGuard(valid().withProperty("spring.servlet.multipart.max-file-size",value)).afterPropertiesSet());
        }
    }
    @Test void refusesUnlimitedHttpRequests() {
        assertThrows(IllegalStateException.class, () -> new ProductionConfigurationGuard(
            valid().withProperty("server.undertow.max-http-post-size", "-1")).afterPropertiesSet());
    }
    @Test void refusesLongLivedTokens() {
        assertThrows(IllegalStateException.class, () -> new ProductionConfigurationGuard(
                valid().withProperty("token.expireTime", "481")).afterPropertiesSet());
        assertThrows(IllegalStateException.class, () -> new ProductionConfigurationGuard(
                valid().withProperty("qinglife.app-token.expire-minutes", "1441")).afterPropertiesSet());
    }
    @Test void refusesBlankOrUnprotectedRedis() {
        assertThrows(IllegalStateException.class, () -> new ProductionConfigurationGuard(
                valid().withProperty("spring.redis.password", " ")).afterPropertiesSet());
        assertThrows(IllegalStateException.class, () -> new ProductionConfigurationGuard(
                valid().withProperty("qinglife.redis.allow-insecure-internal", "false")).afterPropertiesSet());
    }
    @Test void validatesOnlyTheSelectedOssProvider() {
        MockEnvironment minio = valid().withProperty("qinglife.oss.provider", "minio")
                .withProperty("cloud-storage.minio.endpoint", "https://storage.qinglife.test")
                .withProperty("cloud-storage.minio.accessKey", "synthetic-access")
                .withProperty("cloud-storage.minio.secretKey", "synthetic-secret")
                .withProperty("cloud-storage.minio.bucketName", "qinglife-media");
        assertDoesNotThrow(() -> new ProductionConfigurationGuard(minio).afterPropertiesSet());
        assertThrows(IllegalStateException.class, () -> new ProductionConfigurationGuard(
                minio.withProperty("cloud-storage.minio.bucketName", "ruoyi")).afterPropertiesSet());
        assertThrows(IllegalStateException.class, () -> new ProductionConfigurationGuard(
                minio.withProperty("cloud-storage.minio.endpoint", "https:///missing-host")).afterPropertiesSet());
    }
}
