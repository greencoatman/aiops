package com.repair.aiops.service.memory;

import com.repair.aiops.model.dto.MessageContext;

/**
 * 业主会话记忆服务接口
 */
public interface ISessionMemoryService {

    /**
     * 保存/追加记忆片段（简单文本方式，兼容旧版本）
     * @param senderId 业主ID
     * @param content 当前对话内容
     */
    void saveMemory(String senderId, String content);

    /**
     * 获取之前的记忆内容（简单文本方式，兼容旧版本）
     * @param senderId 业主ID
     * @return 拼接后的历史文本
     */
    String getMemory(String senderId);

    /**
     * 清除记忆（当工单成功生成或过期后调用）
     * @param senderId 业主ID
     */
    void clearMemory(String senderId);
    
    /**
     * 保存消息上下文（支持多条消息收集）
     * @param senderId 业主ID
     * @param content 消息内容
     * @param timestamp 消息时间戳
     * @param imageUrl 图片URL（可选）
     */
    void saveMessageContext(String senderId, String content, Long timestamp, String imageUrl);
    
    /**
     * 获取消息上下文（包含所有收集的消息）
     * @param senderId 业主ID
     * @return 消息上下文
     */
    MessageContext getMessageContext(String senderId);
}
