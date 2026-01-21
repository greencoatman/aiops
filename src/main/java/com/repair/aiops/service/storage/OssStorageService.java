package com.repair.aiops.service.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
public class OssStorageService {
    @Value("${oss.enabled:false}")
    private boolean enabled;

    @Value("${oss.endpoint:}")
    private String endpoint;

    @Value("${oss.access-key-id:}")
    private String accessKeyId;

    @Value("${oss.access-key-secret:}")
    private String accessKeySecret;

    @Value("${oss.bucket:}")
    private String bucket;

    @Value("${oss.public-url:}")
    private String publicUrl;

    @Value("${oss.object-prefix:wecom/images/}")
    private String objectPrefix;

    public String upload(byte[] content, String contentType) {
        if (!enabled) {
            log.warn("OSS 上传未启用");
            return null;
        }
        if (!StringUtils.hasText(endpoint) || !StringUtils.hasText(accessKeyId)
                || !StringUtils.hasText(accessKeySecret) || !StringUtils.hasText(bucket)) {
            log.warn("OSS 配置不完整，无法上传");
            return null;
        }
        if (content == null || content.length == 0) {
            return null;
        }

        String extension = guessExtension(contentType);
        String objectName = buildObjectName(extension);

        OSS ossClient = null;
        try {
            ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType != null ? contentType : "application/octet-stream");
            metadata.setContentLength(content.length);
            ossClient.putObject(bucket, objectName, new ByteArrayInputStream(content), metadata);
            return buildPublicUrl(objectName);
        } catch (Exception e) {
            log.error("OSS 上传失败: {}", e.getMessage(), e);
            return null;
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }

    private String buildObjectName(String extension) {
        String datePath = LocalDate.now().toString().replace("-", "/");
        String name = UUID.randomUUID().toString().replace("-", "");
        String prefix = StringUtils.hasText(objectPrefix) ? objectPrefix : "";
        return prefix + datePath + "/" + name + extension;
    }

    private String buildPublicUrl(String objectName) {
        if (StringUtils.hasText(publicUrl)) {
            String base = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
            return base + "/" + objectName;
        }
        String ep = endpoint.startsWith("http") ? endpoint : "https://" + endpoint;
        return ep.replace("://", "://" + bucket + ".") + "/" + objectName;
    }

    private String guessExtension(String contentType) {
        if (contentType == null) {
            return ".jpg";
        }
        if (contentType.contains("png")) {
            return ".png";
        }
        if (contentType.contains("gif")) {
            return ".gif";
        }
        if (contentType.contains("webp")) {
            return ".webp";
        }
        return ".jpg";
    }
}
