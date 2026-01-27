package com.repair.aiops.model.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.repair.aiops.model.enums.IntentType;
import com.repair.aiops.model.enums.UrgencyLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 解析后的工单草稿
 * 对应架构图中的 "AI Parsing" 结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketDraft {

    // --- 核心判断 ---

    /**
     * 是否触发业务流程：true 表示是报修/投诉/咨询；false 表示是闲聊/表情包
     */
    private boolean actionable;

    // --- 实体槽位提取 (Entity Extraction) ---

    /**
     * 意图分类：报修、投诉、咨询、闲聊
     */
    private IntentType intent;

    /**
     * 问题分类：如 水电、保洁、门窗、噪音、公区等
     */
    private String category;

    /**
     * 房号或具体位置：如 3-201 或 负一楼停车场
     */
    private String location;

    /**
     * 问题详情摘要：AI 会结合文字和图片内容生成一段简洁的描述
     */
    private String description;

    /**
     * 紧急程度：HIGH, MEDIUM, LOW
     */
    private UrgencyLevel urgency;

    // --- 客服辅助 (Agent Copilot) ---

    /**
     * 缺失的关键信息：例如业主没说具体位置，AI 会在这里标注 ["缺少具体位置"]
     */
    @JsonDeserialize(using = StringToListDeserializer.class)
    private java.util.List<String> missingInfo;

    /**
     * 推荐追问语：AI 生成一段话供管家一键点击发送，如 "好的收到，请问是客厅还是卧室的灯坏了？"
     */
    private String suggestedReply;

    /**
     * 预约时间：用户提到的希望处理的时间，格式：yyyy-MM-dd HH:mm 或相对时间描述
     * 例如："明天下午"、"下周一"、"现在"
     */
    private String scheduledTime;

    /**
     * 置信度：AI分析的置信程度，用于判断是否需要人工确认
     * 范围：0.0-1.0，越高表示AI越确信
     */
    private Double confidence;

    /**
     * 图片与文字一致性：true表示图片和文字描述一致，false表示不一致需人工确认
     */
    private Boolean imageTextConsistent;

    // --- 身份信息 (由后端 Service 补充) ---

    private String ownerName;      // 业主姓名
    private String roomNumber;     // 关联房号
    private String senderId;       // 原始企微/微信 ID

    // --- 自定义反序列化器 ---
    public static class StringToListDeserializer extends JsonDeserializer<List<String>> {
        @Override
        public List<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (p.hasToken(com.fasterxml.jackson.core.JsonToken.VALUE_STRING)) {
                String value = p.getText();
                if (value == null || value.trim().isEmpty()) {
                    return new ArrayList<>();
                }
                // 兼容单字符串的情况
                List<String> list = new ArrayList<>();
                list.add(value);
                return list;
            }
            return p.readValueAs(new TypeReference<List<String>>() {});
        }
    }
}
