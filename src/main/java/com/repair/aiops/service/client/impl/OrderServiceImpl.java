package com.repair.aiops.service.client.impl;

import com.repair.aiops.model.dto.OrderRequest;
import com.repair.aiops.model.dto.OrderResponse;
import com.repair.aiops.service.client.IOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * 下单服务实现
 * 使用RestTemplate调用外部系统的下单API
 */
@Slf4j
@Service
public class OrderServiceImpl implements IOrderService {

    private final RestTemplate restTemplate;
    
    /**
     * 外部系统下单API的URL
     */
    @Value("${aiops.order.api-url:http://localhost:8080/api/orders}")
    private String orderApiUrl;
    
    /**
     * 外部系统API的认证Token（如果需要）
     */
    @Value("${aiops.order.api-token:}")
    private String apiToken;
    
    /**
     * 是否启用自动下单（配置为false时只保存草稿，不调用下单接口）
     */
    @Value("${aiops.order.enabled:false}")
    private boolean orderEnabled;

    public OrderServiceImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public OrderResponse createOrder(OrderRequest request) {
        if (!orderEnabled) {
            log.info("自动下单功能未启用，跳过下单调用");
            return OrderResponse.builder()
                    .success(false)
                    .message("自动下单功能未启用")
                    .build();
        }
        
        if (request == null) {
            log.warn("下单请求为空");
            return OrderResponse.builder()
                    .success(false)
                    .errorMessage("下单请求不能为空")
                    .build();
        }
        
        try {
            log.info("调用外部下单接口：url={}, request={}", orderApiUrl, request);
            
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (apiToken != null && !apiToken.trim().isEmpty()) {
                headers.set("Authorization", "Bearer " + apiToken);
            }
            
            // 创建请求实体
            HttpEntity<OrderRequest> requestEntity = new HttpEntity<>(request, headers);
            
            // 发送POST请求
            ResponseEntity<OrderResponse> response = restTemplate.exchange(
                    orderApiUrl,
                    HttpMethod.POST,
                    requestEntity,
                    OrderResponse.class
            );
            
            OrderResponse orderResponse = response.getBody();
            if (orderResponse != null && Boolean.TRUE.equals(orderResponse.getSuccess())) {
                log.info("下单成功：orderId={}, senderId={}", orderResponse.getOrderId(), request.getSenderId());
            } else {
                log.warn("下单失败：response={}, senderId={}", orderResponse, request.getSenderId());
            }
            
            return orderResponse != null ? orderResponse : OrderResponse.builder()
                    .success(false)
                    .errorMessage("外部系统返回空响应")
                    .build();
                    
        } catch (RestClientException e) {
            log.error("调用外部下单接口异常：url={}, error={}", orderApiUrl, e.getMessage(), e);
            return OrderResponse.builder()
                    .success(false)
                    .errorCode("API_CALL_ERROR")
                    .errorMessage("调用外部下单接口失败：" + e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("下单处理异常：request={}, error={}", request, e.getMessage(), e);
            return OrderResponse.builder()
                    .success(false)
                    .errorCode("INTERNAL_ERROR")
                    .errorMessage("下单处理异常：" + e.getMessage())
                    .build();
        }
    }
}
