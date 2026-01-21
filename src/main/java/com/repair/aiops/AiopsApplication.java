package com.repair.aiops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AiopsApplication {

    public static void main(String[] args) {
        // 强制设置 API Key，解决配置加载顺序问题
        System.setProperty("spring.ai.tongyi.api-key", "sk-a30b883429774cd9a76c7554b39f4c2a");
        System.setProperty("spring.cloud.ai.tongyi.api-key", "sk-a30b883429774cd9a76c7554b39f4c2a");
        SpringApplication.run(AiopsApplication.class, args);
    }

}
