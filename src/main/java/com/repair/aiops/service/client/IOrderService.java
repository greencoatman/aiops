package com.repair.aiops.service.client;

import com.repair.aiops.model.dto.OrderRequest;
import com.repair.aiops.model.dto.OrderResponse;

/**
 * 下单服务接口
 * 用于调用外部系统的下单API
 */
public interface IOrderService {
    
    /**
     * 创建工单
     * @param request 下单请求
     * @return 下单响应
     */
    OrderResponse createOrder(OrderRequest request);
}
