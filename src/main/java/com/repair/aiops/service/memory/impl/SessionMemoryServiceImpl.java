package com.repair.aiops.service.memory.impl;


import com.alibaba.fastjson.JSON;
import com.repair.aiops.model.dto.MessageContext;
import com.repair.aiops.service.memory.ISessionMemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SessionMemoryServiceImpl implements ISessionMemoryService {

    private final RedisTemplate<String, Object> redisTemplate;

    // Redis Key 的前缀
    private static final String MEMORY_KEY_PREFIX = "aiops:memory:";
    private static final String CONTEXT_KEY_PREFIX = "aiops:context:";
    
    // 记忆有效期（分钟），从配置文件读取，默认30分钟
    @Value("${aiops.memory.expire-time:30}")
    private long expireTime;

    public SessionMemoryServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void saveMemory(String senderId, String content) {
        if (senderId == null || content == null || content.trim().isEmpty()) {
            log.warn("保存记忆失败：senderId或content为空");
            return;
        }
        
        try {
            String key = MEMORY_KEY_PREFIX + senderId;
            // 获取现有的记忆内容
            Object existing = redisTemplate.opsForValue().get(key);

            String newContent;
            if (existing == null) {
                newContent = content.trim();
            } else {
                // 将新内容追加到旧内容后面，用分号隔开，方便 AI 理解
                // 过滤掉明显的闲聊内容（如"收到"、"谢谢"等）
                String filteredContent = filterNoiseContent(content);
                if (filteredContent != null && !filteredContent.isEmpty()) {
                    newContent = existing.toString() + "；" + filteredContent;
                } else {
                    // 如果是闲聊内容，不追加，但保持现有记忆
                    newContent = existing.toString();
                }
            }

            // 存入 Redis 并设置/刷新有效期
            redisTemplate.opsForValue().set(key, newContent, expireTime, TimeUnit.MINUTES);
            log.debug("保存记忆成功：senderId={}, 有效期={}分钟", senderId, expireTime);
        } catch (Exception e) {
            log.error("保存记忆异常：senderId={}, error={}", senderId, e.getMessage(), e);
            // 不影响主流程，只记录日志
        }
    }

    @Override
    public String getMemory(String senderId) {
        if (senderId == null) {
            return "";
        }
        
        try {
            Object memory = redisTemplate.opsForValue().get(MEMORY_KEY_PREFIX + senderId);
            return memory != null ? memory.toString() : "";
        } catch (Exception e) {
            log.error("获取记忆异常：senderId={}, error={}", senderId, e.getMessage(), e);
            return "";
        }
    }

    /**
     * 过滤闲聊内容，只保留业务相关信息
     * 过滤规则：单字回复、常见礼貌用语等
     */
    private String filterNoiseContent(String content) {
        if (content == null) {
            return null;
        }
        
        String trimmed = content.trim();
        
        // 过滤单字回复
        if (trimmed.length() <= 1) {
            return null;
        }
        
        // 过滤常见的无意义回复
        String[] noiseKeywords = {"收到", "谢谢", "好的", "OK", "ok", "知道了", "明白"};
        for (String keyword : noiseKeywords) {
            if (trimmed.equals(keyword) || trimmed.equals(keyword + "。") || trimmed.equals(keyword + "！")) {
                return null;
            }
        }
        
        return trimmed;
    }
    
    @Override
    public void saveMessageContext(String senderId, String content, Long timestamp, String imageUrl) {
        if (senderId == null) {
            log.warn("保存消息上下文失败：senderId为空");
            return;
        }
        
        try {
            String key = CONTEXT_KEY_PREFIX + senderId;
            
            // 获取现有的消息上下文
            MessageContext context = getMessageContext(senderId);
            if (context == null) {
                context = MessageContext.builder()
                        .messages(new java.util.ArrayList<>())
                        .build();
            }
            
            // 过滤闲聊内容
            String filteredContent = filterNoiseContent(content);
            if (filteredContent != null && !filteredContent.isEmpty()) {
                // 添加新消息
                context.addMessage(filteredContent, timestamp, imageUrl);
                
                // 序列化并存储
                String contextJson = JSON.toJSONString(context);
                redisTemplate.opsForValue().set(key, contextJson, expireTime, TimeUnit.MINUTES);
                log.debug("保存消息上下文成功：senderId={}, 消息数={}", senderId, context.getMessages().size());
            }
        } catch (Exception e) {
            log.error("保存消息上下文异常：senderId={}, error={}", senderId, e.getMessage(), e);
        }
    }
    
    @Override
    public MessageContext getMessageContext(String senderId) {
        if (senderId == null) {
            return null;
        }
        
        try {
            String key = CONTEXT_KEY_PREFIX + senderId;
            Object contextObj = redisTemplate.opsForValue().get(key);
            
            if (contextObj == null) {
                return null;
            }
            
            // 反序列化
            if (contextObj instanceof String) {
                return JSON.parseObject((String) contextObj, MessageContext.class);
            }
            
            return null;
        } catch (Exception e) {
            log.error("获取消息上下文异常：senderId={}, error={}", senderId, e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public void clearMemory(String senderId) {
        if (senderId == null) {
            return;
        }
        
        try {
            // 清除简单记忆
            redisTemplate.delete(MEMORY_KEY_PREFIX + senderId);
            // 清除消息上下文
            redisTemplate.delete(CONTEXT_KEY_PREFIX + senderId);
            log.debug("清除记忆成功：senderId={}", senderId);
        } catch (Exception e) {
            log.error("清除记忆异常：senderId={}, error={}", senderId, e.getMessage(), e);
        }
    }
}
