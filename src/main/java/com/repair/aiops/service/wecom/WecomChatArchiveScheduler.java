package com.repair.aiops.service.wecom;

import com.repair.aiops.controller.AgentController;
import com.repair.aiops.model.dto.GroupMsgDTO;
import com.repair.aiops.model.dto.wecom.WecomChatDataItem;
import com.repair.aiops.model.dto.wecom.WecomChatDataResponse;
import com.repair.aiops.service.storage.OssStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Component
public class WecomChatArchiveScheduler {
    private final WecomChatArchiveService wecomChatArchiveService;
    private final WecomChatMessageParser wecomChatMessageParser;
    private final OssStorageService ossStorageService;
    private final AgentController agentController;
    private final StringRedisTemplate redisTemplate;
    
    private static final String REDIS_SEQ_KEY = "wecom:chat:archive:seq";

    @Value("${wecom.chat.archive.poll.enabled:false}")
    private boolean enabled;

    @Value("${wecom.chat.archive.poll.limit:50}")
    private int limit;
    
    @Value("${wecom.chat.archive.poll.initial-seq:0}")
    private long initialSeq;

    @Value("${wecom.chat.archive.allowed-groups:}")
    private String allowedGroups;

    public WecomChatArchiveScheduler(WecomChatArchiveService wecomChatArchiveService,
                                     WecomChatMessageParser wecomChatMessageParser,
                                     OssStorageService ossStorageService,
                                     AgentController agentController,
                                     StringRedisTemplate redisTemplate) {
        this.wecomChatArchiveService = wecomChatArchiveService;
        this.wecomChatMessageParser = wecomChatMessageParser;
        this.ossStorageService = ossStorageService;
        this.agentController = agentController;
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(fixedDelayString = "${wecom.chat.archive.poll.interval-ms:30000}")
    public void pollChatArchive() {
        if (!enabled) {
            return;
        }

        // 分布式锁 Key
        String lockKey = "wecom:chat:archive:lock";
        // 尝试获取锁（过期时间设为 50秒，略大于定时任务间隔30秒，防止死锁）
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", java.time.Duration.ofSeconds(50));
        
        if (Boolean.FALSE.equals(locked)) {
            log.debug("上一次任务尚未结束或锁未释放，跳过本次拉取");
            return;
        }

        try {
            // 计算今天下午 16:00:00 的时间戳 (临时需求：只处理16点之后的消息)
            long cutOffTime = java.time.LocalDate.now().atTime(16, 0).toInstant(java.time.ZoneOffset.of("+8")).toEpochMilli();
            
            // 1. 获取当前 seq (优先从 Redis 获取，没有则使用初始配置)
            long seq = initialSeq;
            String seqStr = redisTemplate.opsForValue().get(REDIS_SEQ_KEY);
            if (StringUtils.hasText(seqStr)) {
                try {
                    seq = Long.parseLong(seqStr);
                } catch (NumberFormatException e) {
                    log.warn("Redis中seq格式错误，重置为初始值: {}", seqStr);
                }
            }
            
            log.debug("准备拉取消息: currentSeq={}, limit={}", seq, limit);

            WecomChatDataResponse response = wecomChatArchiveService.fetchChatData(seq, limit);
            if (response == null || response.getChatdata() == null || response.getChatdata().isEmpty()) {
                return;
            }

            List<WecomChatDataItem> chatData = response.getChatdata();
            int analyzed = 0;
            int skipped = 0;
            for (WecomChatDataItem item : chatData) {
                // ... (原有逻辑保持不变)
                String decrypted = item.getDecryptChatMsg();
                if (!StringUtils.hasText(decrypted)) {
                    skipped++;
                    continue;
                }
                GroupMsgDTO msg = wecomChatMessageParser.parse(decrypted);
                if (msg == null) {
                    skipped++;
                    continue;
                }

                // --- 新增：提前进行白名单过滤（静默跳过无关群） ---
                if (StringUtils.hasText(allowedGroups)) {
                    boolean allowed = false;
                    String[] groups = allowedGroups.split(",");
                    for (String g : groups) {
                        if (g.trim().equals(msg.getGroupId())) {
                            allowed = true;
                            break;
                        }
                    }
                    if (!allowed) {
                        // 不在白名单，直接跳过，不打印日志
                        skipped++;
                        continue;
                    }
                }
                
                // 调试日志：打印每条消息的时间戳
                log.info("检查消息时间: seq={}, msgTime={}, cutOffTime={}, content={}", 
                        item.getSeq(), msg.getTimestamp(), cutOffTime, 
                        (msg.getContent() != null && msg.getContent().length() > 10) ? msg.getContent().substring(0, 10) + "..." : msg.getContent());
                
                // --- 修改：只处理指定时间之后的消息 ---
                // 如果时间戳为空，或者早于截止时间，跳过
                if (msg.getTimestamp() == null || msg.getTimestamp() < cutOffTime) {
                    if (msg.getTimestamp() == null) {
                        log.warn("跳过无时间戳消息: seq={}", item.getSeq());
                    }
                    skipped++;
                    continue;
                }

                // 图片处理：如果是 sdkfileid，则拉取并上传 OSS
                if (StringUtils.hasText(msg.getImageUrl())) {
                    String sdkFileId = extractSdkFileId(msg.getImageUrl());
                    if (sdkFileId != null) {
                        ResponseEntity<byte[]> media = wecomChatArchiveService.fetchMedia(sdkFileId);
                        if (media.getStatusCode().is2xxSuccessful() && media.getBody() != null) {
                            String contentType = media.getHeaders().getContentType() != null
                                    ? media.getHeaders().getContentType().toString()
                                    : "image/jpeg";
                            String ossUrl = ossStorageService.upload(media.getBody(), contentType);
                            if (ossUrl != null) {
                                msg.setImageUrl(ossUrl);
                            }
                        }
                    }
                }
                // 调用 AgentController 处理消息 (利用其白名单逻辑)
                agentController.onGroupMessage(msg);
                analyzed++;
            }

            // 2. 更新 seq 到 Redis
            if (response.getNext_seq() != null && response.getNext_seq() > seq) {
                redisTemplate.opsForValue().set(REDIS_SEQ_KEY, String.valueOf(response.getNext_seq()));
                log.info("企业微信存档进度已更新: oldSeq={}, newSeq={}", seq, response.getNext_seq());
            }
            
            log.info("企业微信存档定时拉取完成: seq={}, nextSeq={}, analyzed={}, skipped={}",
                    seq, response.getNext_seq(), analyzed, skipped);
        } finally {
            // 释放锁
            redisTemplate.delete(lockKey);
        }
    }

    private String extractSdkFileId(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return null;
        }
        if (!imageUrl.startsWith("http")) {
            return imageUrl;
        }
        int idx = imageUrl.lastIndexOf('/');
        if (idx >= 0 && idx + 1 < imageUrl.length()) {
            return imageUrl.substring(idx + 1);
        }
        return null;
    }
}
