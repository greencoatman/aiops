package com.repair.aiops.service.client.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;
    
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

    public OrderServiceImpl(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public OrderResponse createOrder(OrderRequest request) {
        if (!orderEnabled) {
            log.info("自动下单功能未启用，跳过下单调用：senderId={}",
                    request != null ? request.getSenderId() : "unknown");
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
            String traceId = org.slf4j.MDC.get("traceId");
            log.info("[traceId={}] [下单请求] 开始调用: url={}, senderId={}, houseId={}, userId={}",
                    traceId, orderApiUrl, request.getSenderId(), request.getHouseId(), request.getUserId());
            
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (apiToken != null && !apiToken.trim().isEmpty()) {
                headers.set("Authorization", "Bearer " + apiToken);
            }
            
            // 创建请求实体
            // 构造外部API需要的特定JSON结构
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("type", 1); // 1-报修
            payload.put("category", 5); // 5-默认分类(或根据request.category映射)
            String description = request.getDescription();
            String location = request.getLocation();
            if (location != null && !location.trim().isEmpty()) {
                if (description == null || description.trim().isEmpty()) {
                    description = "位置：" + location.trim();
                } else if (!description.contains(location.trim())) {
                    description = "位置：" + location.trim() + "；" + description.trim();
                }
            }
            payload.put("description", description);
            payload.put("location", request.getLocation());
            payload.put("appointmentStartTime", request.getAppointmentStartTime());
            payload.put("appointmentEndTime", request.getAppointmentEndTime());
            payload.put("fileList", request.getFileList() != null ? request.getFileList() : new java.util.ArrayList<>());
            payload.put("houseId", request.getHouseId() != null ? request.getHouseId() : 918);
            payload.put("userId", request.getUserId());
            
            // 打印完整请求体
            try {
                String jsonPayload = objectMapper.writeValueAsString(payload);
                log.info("[traceId={}] [下单请求] Payload: {}", traceId, jsonPayload);
            } catch (Exception e) {
                log.warn("[traceId={}] 序列化请求参数失败", traceId);
            }

            HttpEntity<java.util.Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);
            
            // 发送POST请求
            // 外部API返回text/plain，需要先获取String，再手动转换
            long start = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.exchange(
                    orderApiUrl,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );
            long duration = System.currentTimeMillis() - start;
            
            String responseBody = response.getBody();
            log.info("[traceId={}] [下单响应] 原始响应: statusCode={}, duration={}ms, body={}", 
                    traceId, response.getStatusCode().value(), duration, responseBody);

            OrderResponse orderResponse = null;
            if (responseBody != null && !responseBody.isEmpty()) {
                try {
                    orderResponse = objectMapper.readValue(responseBody, OrderResponse.class);
                } catch (Exception e) {
                    log.warn("解析下单响应JSON失败: {}", responseBody, e);
                }
            }

            // 兼容外部接口仅返回 code/msg 的情况
            if (orderResponse == null || orderResponse.getSuccess() == null) {
                try {
                    com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(responseBody);
                    int code = node.has("code") ? node.get("code").asInt() : -1;
                    String msg = node.has("msg") ? node.get("msg").asText() : null;
                    if (code == 200) {
                        orderResponse = OrderResponse.builder()
                                .success(true)
                                .message(msg != null ? msg : "下单成功")
                                .build();
                    }
                } catch (Exception e) {
                    log.warn("兼容解析下单响应失败: {}", responseBody, e);
                }
            }

            if (orderResponse != null && Boolean.TRUE.equals(orderResponse.getSuccess())) {
                log.info("[traceId={}] [下单结果] 成功: orderId={}", traceId, orderResponse.getOrderId());
            } else {
                log.warn("[traceId={}] [下单结果] 失败或未成功标识: {}", traceId, orderResponse);
            }
            
            return orderResponse != null ? orderResponse : OrderResponse.builder()
                    .success(false)
                    .errorMessage("外部系统响应解析失败或为空")
                    .build();
                    
        } catch (RestClientException e) {
            String traceId = org.slf4j.MDC.get("traceId");
            log.error("[traceId={}] [下单异常] 网络/HTTP错误: url={}, error={}", traceId, orderApiUrl, e.getMessage(), e);
            return OrderResponse.builder()
                    .success(false)
                    .errorCode("API_CALL_ERROR")
                    .errorMessage("调用外部下单接口失败：" + e.getMessage())
                    .build();
        } catch (Exception e) {
            String traceId = org.slf4j.MDC.get("traceId");
            log.error("[traceId={}] [下单异常] 系统内部错误: error={}", traceId, e.getMessage(), e);
            return OrderResponse.builder()
                    .success(false)
                    .errorCode("INTERNAL_ERROR")
                    .errorMessage("下单处理异常：" + e.getMessage())
                    .build();
        }
    }
}
