package com.repair.aiops.service.core;

import com.repair.aiops.model.dto.GroupMsgDTO;
import com.repair.aiops.model.dto.TicketDraft;
import com.repair.aiops.model.enums.IntentType;
import com.repair.aiops.service.business.IOwnerService;
import com.repair.aiops.service.memory.ISessionMemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
public class AgentService {
    private final ChatClient chatClient;
    private final PromptService promptService;
    private final MessageDedupService dedupService;
    
    @Autowired
    private ISessionMemoryService memoryService;
    
    @Autowired
    private IOwnerService ownerService;
    
    private final BeanOutputConverter<TicketDraft> converter = new BeanOutputConverter<>(TicketDraft.class);
    
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public AgentService(ChatClient chatClient, PromptService promptService, MessageDedupService dedupService) {
        this.chatClient = chatClient;
        this.promptService = promptService;
        this.dedupService = dedupService;
    }

    public TicketDraft analyze(GroupMsgDTO msg) {
        if (msg == null || msg.getSenderUserId() == null) {
            log.error("分析消息失败：消息或发送者ID为空");
            throw new IllegalArgumentException("消息或发送者ID不能为空");
        }

        try {
            // 0.1 纯数字/无效内容直接视为闲聊，避免误触发追问
            String rawContent = msg.getContent() != null ? msg.getContent().trim() : "";
            if (rawContent.matches("^\\d{1,10}$") && (msg.getImageUrl() == null || msg.getImageUrl().isEmpty())) {
                log.info("识别为闲聊(纯数字)，不触发处理：senderId={}, content={}", msg.getSenderUserId(), rawContent);
                return TicketDraft.builder()
                        .actionable(false)
                        .intent(IntentType.NOISE)
                        .confidence(0.2)
                        .suggestedReply(null)
                        .missingInfo(new java.util.ArrayList<>())
                        .build();
            }

            // 0. 消息去重检查
            if (dedupService.isDuplicate(msg.getSenderUserId(), msg.getContent())) {
                log.info("检测到重复消息，跳过处理：senderId={}, content={}", 
                        msg.getSenderUserId(), msg.getContent());
                // 返回一个标记为重复的草稿（或返回null，由Controller处理）
                return null;
            }

            // 1. 获取上下文（身份锚定 + 历史记忆）
            String ownerInfo;
            try {
                ownerInfo = ownerService.getOwnerInfo(msg.getSenderUserId());
            } catch (Exception e) {
                log.error("获取业主信息失败：senderId={}, error={}", msg.getSenderUserId(), e.getMessage(), e);
                ownerInfo = "未知身份业主";
            }
            
            // 获取消息上下文（支持多条消息收集）
            com.repair.aiops.model.dto.MessageContext messageContext = 
                    memoryService.getMessageContext(msg.getSenderUserId());
            
            // 构建历史记忆文本（用于AI分析）
            String history;
            if (messageContext != null && messageContext.getMessages() != null 
                    && !messageContext.getMessages().isEmpty()) {
                // 使用消息上下文的合并内容
                history = messageContext.getMergedContent();
                log.debug("使用消息上下文：senderId={}, 消息数={}, 合并内容长度={}", 
                        msg.getSenderUserId(), messageContext.getMessages().size(), history);
            } else {
                // 降级到简单记忆方式（兼容旧版本）
                history = memoryService.getMemory(msg.getSenderUserId());
            }

            // 2. 格式化时间信息
            String currentTime;
            if (msg.getTimestamp() != null && msg.getTimestamp() > 0) {
                LocalDateTime dateTime = LocalDateTime.ofEpochSecond(msg.getTimestamp() / 1000, 0, 
                        java.time.ZoneOffset.of("+8"));
                currentTime = dateTime.format(TIME_FORMATTER);
            } else {
                currentTime = LocalDateTime.now().format(TIME_FORMATTER);
            }

            // 3. 准备提示词 (System Prompt)
            String systemPrompt = promptService.buildSystemPrompt(ownerInfo, history, 
                    converter.getFormat(), currentTime);

            // 4. 构建用户消息 (User Message) - 支持多模态图片
            // 如果有多条消息的图片，需要合并所有图片
            UserMessage userMessage;
            try {
                List<Media> imageMedias = new java.util.ArrayList<>();
                
                // 收集所有图片（包括历史消息和当前消息）
                if (messageContext != null && messageContext.getImageUrls() != null) {
                    for (String imageUrl : messageContext.getImageUrls()) {
                        if (imageUrl != null && imageUrl.startsWith("http")) {
                            try {
                                imageMedias.add(new Media(MimeTypeUtils.IMAGE_JPEG, new java.net.URL(imageUrl)));
                            } catch (Exception e) {
                                log.warn("添加历史图片失败：imageUrl={}, error={}", imageUrl, e.getMessage());
                            }
                        }
                    }
                }
                
                // 添加当前消息的图片
                if (msg.getImageUrl() != null && !msg.getImageUrl().isEmpty()) {
                    if (msg.getImageUrl().startsWith("http")) {
                        try {
                            imageMedias.add(new Media(MimeTypeUtils.IMAGE_JPEG, new java.net.URL(msg.getImageUrl())));
                        } catch (Exception e) {
                            log.warn("添加当前图片失败：imageUrl={}, error={}", msg.getImageUrl(), e.getMessage());
                        }
                    } else {
                        log.warn("当前图片URL无效（非HTTP链接，可能OSS上传失败）：imageUrl={}", msg.getImageUrl());
                    }
                }
                
                // 构建用户消息（合并历史上下文，避免上下文丢失）
                String userContent = msg.getContent() != null ? msg.getContent() : "";
                String combinedContent = userContent;
                if (history != null && !history.isEmpty()) {
                    if (userContent == null || userContent.trim().isEmpty()) {
                        combinedContent = history;
                    } else if (!history.contains(userContent)) {
                        combinedContent = history + "；" + userContent;
                    } else {
                        combinedContent = history;
                    }
                }
                if (!imageMedias.isEmpty()) {
                    userMessage = new UserMessage(combinedContent, imageMedias);
                    log.debug("构建多模态消息：senderId={}, 图片数={}", msg.getSenderUserId(), imageMedias.size());
                } else {
                    userMessage = new UserMessage(combinedContent);
                }
            } catch (Exception e) {
                log.warn("构建多模态消息失败，退化为纯文本模式：senderId={}, error={}", 
                        msg.getSenderUserId(), e.getMessage());
                userMessage = new UserMessage(msg.getContent() != null ? msg.getContent() : "");
            }

            // 5. 调用 AI 决策大脑
            TicketDraft draft;
            try {
                String traceId = org.slf4j.MDC.get("traceId");
                String contentPreview = msg.getContent() != null ? msg.getContent().trim() : "";
                if (contentPreview.length() > 50) {
                    contentPreview = contentPreview.substring(0, 50) + "...";
                }
                log.info("[traceId={}] [AI请求] 开始AI分析：senderId={}, contentLength={}, hasHistory={}, hasImage={}, content={}",
                        traceId,
                        msg.getSenderUserId(),
                        msg.getContent() != null ? msg.getContent().length() : 0,
                        history != null && !history.isEmpty(),
                        msg.getImageUrl() != null && !msg.getImageUrl().isEmpty(),
                        contentPreview);
                
                // 仅在 DEBUG 级别打印完整 Prompt，避免生产日志过大
                if (log.isDebugEnabled()) {
                    log.debug("[traceId={}] [AI请求] System Prompt 预览: {}", traceId, 
                            systemPrompt.length() > 500 ? systemPrompt.substring(0, 500) + "..." : systemPrompt);
                }
                
                long aiStart = System.currentTimeMillis();
                
                // 实际调用
                org.springframework.ai.chat.model.ChatResponse response = chatClient.prompt()
                        .system(systemPrompt)
                        .messages(userMessage)
                        .call()
                        .chatResponse();
                
                // 提取实体对象
                draft = converter.convert(response.getResult().getOutput().getContent());
                
                long aiDuration = System.currentTimeMillis() - aiStart;
                
                // 获取 Token 使用情况 (如果支持)
                org.springframework.ai.chat.metadata.Usage usage = response.getMetadata().getUsage();
                // 简化处理：直接转字符串，避免因 Spring AI 版本差异导致的方法名报错
                String tokenUsage = (usage != null) ? usage.toString() : "unknown";

                log.info("[traceId={}] [AI响应] 分析完成: duration={}ms, usage=[{}], result={actionable={}, intent={}, confidence={}}",
                        traceId, aiDuration, tokenUsage,
                        draft != null ? draft.isActionable() : "null",
                        draft != null ? draft.getIntent() : "null",
                        draft != null ? draft.getConfidence() : "null");
                if (draft != null && draft.getIntent() == IntentType.NOISE) {
                    log.info("[traceId={}] [AI响应] NOISE内容: {}", traceId, contentPreview);
                }

            } catch (Exception e) {
                log.error("[AI异常] AI分析调用失败：senderId={}, error={}", msg.getSenderUserId(), e.getMessage(), e);
                throw new RuntimeException("AI分析失败: " + e.getMessage(), e);
            }

            // 6. 闭环记忆处理逻辑
            if (draft != null) {
                // 处理更正消息：如果是更正消息，清除之前的记忆
                if (dedupService.isCorrection(msg.getContent())) {
                    log.info("检测到更正消息，清除历史记忆：senderId={}", msg.getSenderUserId());
                    memoryService.clearMemory(msg.getSenderUserId());
                    // 更正消息也保存为新记忆
                    memoryService.saveMemory(msg.getSenderUserId(), msg.getContent());
                } else if (draft.isActionable()) {
                    // 情况 A：信息全了（有房号、有事由），清除该业主的临时记忆，准备下工单
                    memoryService.clearMemory(msg.getSenderUserId());
                } else if (draft.getIntent() != IntentType.NOISE) {
                    // 情况 B：不是闲聊但信息不全（比如没说房号），将当前消息存入消息上下文
                    // 使用新的消息上下文机制，支持多条消息收集
                    memoryService.saveMessageContext(
                            msg.getSenderUserId(), 
                            msg.getContent(), 
                            msg.getTimestamp() != null ? msg.getTimestamp() : System.currentTimeMillis(),
                            msg.getImageUrl()
                    );
                    // 同时保存简单记忆（兼容性）
                    memoryService.saveMemory(msg.getSenderUserId(), msg.getContent());
                }

                // 补充元数据
                draft.setSenderId(msg.getSenderUserId());
            }

            return draft;
        } catch (Exception e) {
            log.error("分析消息异常：senderId={}, error={}", msg.getSenderUserId(), e.getMessage(), e);
            throw e;
        }
    }
}
