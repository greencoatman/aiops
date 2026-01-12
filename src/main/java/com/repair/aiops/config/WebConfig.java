package com.repair.aiops.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Web配置类
 * 配置RestTemplate用于调用外部API
 */
@Configuration
public class WebConfig {

    /**
     * 配置RestTemplate，用于调用外部系统的API
     * 设置连接超时和读取超时，提高系统稳定性
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))  // 连接超时5秒
                .setReadTimeout(Duration.ofSeconds(10))    // 读取超时10秒
                .build();
    }
}
