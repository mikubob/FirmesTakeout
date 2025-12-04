package com.sky.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "sky.alioss")
public class AliOssProperties {
    private String endpoint;
    private String bucketName;
    private String region = "cn-beijing"; // 默认region
}