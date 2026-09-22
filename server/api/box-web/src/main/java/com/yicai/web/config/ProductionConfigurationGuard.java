package com.yicai.web.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Refuse to start a production process with the repository's example credentials. */
@Component
@Profile("prod")
public class ProductionConfigurationGuard implements InitializingBean {
    private static final Set<String> OSS_PROVIDERS = new HashSet<>(Arrays.asList(
            "disabled", "minio", "qiniu", "aliyun", "qcloud"));
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
        boundedMinutes("token.expireTime", 1, 480);
        boundedMinutes("qinglife.app-token.expire-minutes", 1, 1440);
        String imagePath = required("ruoyi.imagePath");
        if (!imagePath.startsWith("https://")) throw new IllegalStateException("Production public image URL must use HTTPS");
        positiveSize("server.undertow.max-http-post-size");
        positiveSize("spring.servlet.multipart.max-file-size");
        positiveSize("spring.servlet.multipart.max-request-size");
        if (environment.getProperty("qinglife.wechat.enabled", Boolean.class, false)) {
            required("qinglife.wechat.app-id");
            required("qinglife.wechat.app-secret");
        }
        String exposed = environment.getProperty("management.endpoints.web.exposure.include", "health");
        if (!"health".equalsIgnoreCase(exposed.replace(" ", "").trim())) {
            throw new IllegalStateException("Production management HTTP exposure must be limited to health");
        }
        validateRedis();
        validateOss();
    }

    private void validateRedis() {
        String host = required("spring.redis.host");
        required("spring.redis.password");
        boolean ssl = environment.getProperty("spring.redis.ssl", Boolean.class, false);
        boolean allowInternal = environment.getProperty("qinglife.redis.allow-insecure-internal", Boolean.class, false);
        if (!ssl && !(allowInternal && isInternalHost(host))) {
            throw new IllegalStateException("Production Redis requires TLS or an explicit private-network exception");
        }
    }

    private void validateOss() {
        String provider = environment.getProperty("qinglife.oss.provider", "disabled").trim().toLowerCase();
        if (!OSS_PROVIDERS.contains(provider)) {
            throw new IllegalStateException("Unsupported production OSS provider: qinglife.oss.provider");
        }
        if ("disabled".equals(provider)) return;
        String prefix = "cloud-storage." + provider + ".";
        String endpointKey = "qiniu".equals(provider) ? prefix + "domain" : prefix + "endpoint";
        String endpoint = notTemplate(endpointKey, required(endpointKey));
        notTemplate(prefix + "bucketName", required(prefix + "bucketName"));
        if ("aliyun".equals(provider)) {
            required(prefix + "accessKeyId"); required(prefix + "accessKeySecret");
        } else if ("qcloud".equals(provider)) {
            required(prefix + "secretId"); required(prefix + "secretKey");
        } else {
            required(prefix + "accessKey"); required(prefix + "secretKey");
        }
        boolean allowInternal = environment.getProperty("qinglife.oss.allow-insecure-internal", Boolean.class, false);
        URI uri;
        try { uri = URI.create(endpoint); }
        catch (IllegalArgumentException error) { throw new IllegalStateException("Invalid production OSS endpoint: " + endpointKey); }
        if (uri.getHost() == null || uri.getHost().trim().isEmpty()) {
            throw new IllegalStateException("Production OSS endpoint must include a host: " + endpointKey);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                && !("http".equalsIgnoreCase(uri.getScheme()) && allowInternal && isInternalHost(uri.getHost()))) {
            throw new IllegalStateException("Production OSS endpoint must use HTTPS: " + endpointKey);
        }
    }

    private boolean isInternalHost(String host) {
        if (host == null) return false;
        String value = host.trim().toLowerCase();
        if ("localhost".equals(value)) return true;
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) return false;
        int[] octets = new int[4];
        try {
            for (int index = 0; index < parts.length; index++) {
                if (parts[index].isEmpty() || !parts[index].matches("[0-9]{1,3}")) return false;
                octets[index] = Integer.parseInt(parts[index]);
                if (octets[index] > 255) return false;
            }
        } catch (NumberFormatException ignored) {
            return false;
        }
        return octets[0] == 10
                || octets[0] == 127
                || (octets[0] == 192 && octets[1] == 168)
                || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31);
    }

    private String notTemplate(String key, String value) {
        String normalized = value.trim().toLowerCase();
        if (normalized.equals("ruoyi") || normalized.contains("xxx") || normalized.contains("example")) {
            throw new IllegalStateException("Production configuration still uses a template value: " + key);
        }
        return value;
    }

    private void boundedMinutes(String key, int minimum, int maximum) {
        try {
            int value = Integer.parseInt(required(key));
            if (value >= minimum && value <= maximum) return;
        } catch (NumberFormatException ignored) {}
        throw new IllegalStateException("Production token lifetime is outside the allowed range: " + key);
    }

    private void positiveSize(String key) {
        try { if(org.springframework.util.unit.DataSize.parse(required(key)).toBytes()>0)return; }
        catch(IllegalArgumentException ignored) {}
        throw new IllegalStateException("Production size limit must be positive: "+key);
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
