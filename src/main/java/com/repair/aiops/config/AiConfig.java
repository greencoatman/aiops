package com.repair.aiops.config; // 请根据你的项目结构修改包名

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

// 确保这是一个 Spring 配置类
@Configuration
public class AiConfig {

    /**
     * 配置 Spring AI 通用 ChatClient。
     * 直接注入 Spring AI 核心接口 ChatModel，Spring Boot 会自动注入具体的 DashScope 实现类。
     * * @param chatModel Spring Boot 自动配置的 ChatModel 实例（即 DashScope 的实现）
     * @return 高级、流式调用的 ChatClient
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        // 使用 builder 模式将底层的 DashScopeChatModel 包装成高层的 ChatClient
        return ChatClient.builder(chatModel)
                .build();
    }

    /**
     * 配置 RedisTemplate，用于管理 Redis 连接和数据序列化。
     * 这是实现 Chat Memory 等功能的基础。
     *
     * @param factory Redis 连接工厂
     * @return 配置好的 RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // Key 和 HashKey 使用 String 序列化
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Value 依然使用 String 序列化（注意：如果存储Java对象，建议使用 GenericJackson2JsonRedisSerializer）
        template.setValueSerializer(new StringRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public org.springframework.boot.CommandLineRunner debugEnv(org.springframework.core.env.Environment env) {
        return args -> {
            System.out.println("DEBUG: spring.ai.tongyi.api-key=" + env.getProperty("spring.ai.tongyi.api-key"));
            System.out.println("DEBUG: spring.cloud.ai.tongyi.api-key=" + env.getProperty("spring.cloud.ai.tongyi.api-key"));
        };
    }
}
