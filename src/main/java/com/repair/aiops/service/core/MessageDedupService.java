package com.repair.aiops.service.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

/**
 * 消息去重服务
 * 用于检测重复消息和识别更正消息
 */
@Slf4j
@Service
public class MessageDedupService {

    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String DEDUP_KEY_PREFIX = "aiops:dedup:";
    
    // 去重时间窗口（秒），从配置文件读取，默认60秒
    @Value("${aiops.message.dedup-window:60}")
    private long dedupWindow;

    public MessageDedupService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 检查消息是否重复
     * @param senderId 发送者ID
     * @param content 消息内容
     * @return true表示是重复消息，false表示新消息
     */
    public boolean isDuplicate(String senderId, String content) {
        if (senderId == null || content == null || content.trim().isEmpty()) {
            return false;
        }
        
        try {
            String hash = calculateHash(senderId, content);
            String key = DEDUP_KEY_PREFIX + senderId + ":" + hash;
            
            // 检查Redis中是否存在
            Object existing = redisTemplate.opsForValue().get(key);
            if (existing != null) {
                log.debug("检测到重复消息：senderId={}, hash={}", senderId, hash);
                return true;
            }
            
            // 不存在则存储，设置过期时间
            redisTemplate.opsForValue().set(key, "1", dedupWindow, TimeUnit.SECONDS);
            return false;
        } catch (Exception e) {
            log.error("检查消息重复异常：senderId={}, error={}", senderId, e.getMessage(), e);
            // 异常情况不拦截，允许通过
            return false;
        }
    }

    /**
     * 检查消息是否为更正消息
     * 通过识别更正关键词来判断
     * @param content 消息内容
     * @return true表示是更正消息
     */
    public boolean isCorrection(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        
        String[] correctionKeywords = {
            "不对", "错了", "更正", "应该是", "应该是", "不是", 
            "弄错了", "说错了", "修改", "改一下"
        };
        
        String lowerContent = content.toLowerCase();
        for (String keyword : correctionKeywords) {
            if (lowerContent.contains(keyword)) {
                log.debug("检测到更正消息：content={}", content);
                return true;
            }
        }
        
        return false;
    }

    /**
     * 计算消息内容的哈希值（用于去重）
     */
    private String calculateHash(String senderId, String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            String input = senderId + ":" + content.trim();
            byte[] hashBytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("计算哈希值失败", e);
            // 降级方案：使用简单的字符串
            return String.valueOf((senderId + content).hashCode());
        }
    }
}
