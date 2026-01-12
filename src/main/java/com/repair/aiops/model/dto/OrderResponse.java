package com.repair.aiops.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 下单响应DTO
 * 外部系统返回的下单结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    /**
     * 是否成功
     */
    private Boolean success;
    
    /**
     * 工单号/订单号
     */
    private String orderId;
    
    /**
     * 消息
     */
    private String message;
    
    /**
     * 错误码（如果失败）
     */
    private String errorCode;
    
    /**
     * 错误信息（如果失败）
     */
    private String errorMessage;
}
