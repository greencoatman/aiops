package com.repair.aiops.model.dto;

import com.repair.aiops.model.enums.IntentType;
import com.repair.aiops.model.enums.UrgencyLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 下单请求DTO
 * 用于调用外部系统的下单接口
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {
    /**
     * 工单类型：报修/投诉/咨询
     */
    private IntentType intent;
    
    /**
     * 问题分类
     */
    private String category;
    
    /**
     * 位置信息
     */
    private String location;
    
    /**
     * 问题描述
     */
    private String description;
    
    /**
     * 紧急程度
     */
    private UrgencyLevel urgency;
    
    /**
     * 发送者ID（业主ID）
     */
    private String senderId;
    
    /**
     * 业主姓名
     */
    private String ownerName;
    
    /**
     * 房号
     */
    private String roomNumber;
    
    /**
     * 预约时间（可选）
     */
    private String scheduledTime;
    
    /**
     * 原始消息内容
     */
    private String originalContent;
    
    /**
     * 群ID
     */
    private String groupId;
}
