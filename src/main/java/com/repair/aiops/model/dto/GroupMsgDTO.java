package com.repair.aiops.model.dto;

import lombok.Data;

/**
 * 群消息DTO
 */
@Data
public class GroupMsgDTO {
    /**
     * 企微群成员ID (用于身份映射)，必填
     */
    private String senderUserId;
    
    /**
     * 群聊ID，必填
     */
    private String groupId;
    
    /**
     * 消息文本，可以为空（纯图片消息）
     */
    private String content;
    
    /**
     * 消息图片URL (如果有)
     */
    private String imageUrl;
    
    /**
     * 消息时间戳（毫秒），可选，如果为空则使用当前时间
     */
    private Long timestamp;
}
