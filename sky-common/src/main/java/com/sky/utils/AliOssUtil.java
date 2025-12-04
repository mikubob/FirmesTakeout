package com.sky.utils;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.auth.EnvironmentVariableCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class AliOssUtil {
    private static final Logger log = LoggerFactory.getLogger(AliOssUtil.class);
    
    private String endpoint;
    private String bucketName;
    private String region = "cn-beijing"; // 默认region

    public AliOssUtil() {
    }
    
    public AliOssUtil(String endpoint, String bucketName, String region) {
        this.endpoint = endpoint;
        this.bucketName = bucketName;
        this.region = region;
    }

    /**
     * 文件上传
     *
     * @param bytes      文件字节数组
     * @param objectName 文件名
     * @return 文件访问URL
     * @throws Exception 上传异常
     */
    public String upload(byte[] bytes, String objectName) throws Exception {
        // 从环境变量中获取访问凭证，运行代码前，确保已设置环境变量OSS_ACCESS_KEY_ID和OSS_ACCESS_KEY_SECRET
        EnvironmentVariableCredentialsProvider credentialsProvider = CredentialsProviderFactory
                .newEnvironmentVariableCredentialsProvider();

        // 获取当前系统日期的字符串,格式为 yyyy/MM
        String dir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        // 生成一个新的不重复的文件名
        String newFileName = UUID.randomUUID() + objectName.substring(objectName.lastIndexOf("."));
        String fullObjectName = dir + "/" + newFileName;

        // 检查必要参数
        if (endpoint == null || bucketName == null) {
            throw new IllegalArgumentException("OSS endpoint 或 bucketName 未配置");
        }

        // 创建OSSClient实例
        OSS ossClient = new OSSClientBuilder()
                .build(endpoint, credentialsProvider);

        try {
            // 创建PutObject请求
            ossClient.putObject(bucketName, fullObjectName, new ByteArrayInputStream(bytes));
            
            // 构建文件访问URL
            String url = "https://" + bucketName + "." + endpoint.replace("https://", "") + "/" + fullObjectName;
            log.info("文件上传成功，访问URL: {}", url);
            return url;
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }
    
    // Getter和Setter方法
    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }
}