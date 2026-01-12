package com.repair.aiops.controller;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.repair.aiops.model.dto.GroupMsgDTO;
import com.repair.aiops.model.dto.OrderRequest;
import com.repair.aiops.model.dto.OrderResponse;
import com.repair.aiops.model.dto.TicketDraft;
import com.repair.aiops.model.entity.TicketDraftEntity;
import com.repair.aiops.service.business.ITicketDraftService;
import com.repair.aiops.service.client.IOrderService;
import com.repair.aiops.service.core.AgentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wechat")
@Slf4j
public class AgentController {

    private final AgentService agentService;
    private final IOrderService orderService;
    
    @Autowired
    private ITicketDraftService draftService; // MyBatis-Plus Service

    public AgentController(AgentService agentService, IOrderService orderService) {
        this.agentService = agentService;
        this.orderService = orderService;
    }


    @PostMapping("/webhook")
    public ResponseEntity<?> onGroupMessage(@RequestBody GroupMsgDTO msg) {
        // 参数校验
        if (msg == null) {
            log.warn("收到空消息请求");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "ERROR", "message", "消息不能为空"));
        }
        
        if (msg.getSenderUserId() == null || msg.getSenderUserId().trim().isEmpty()) {
            log.warn("收到无效消息：senderUserId为空");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "ERROR", "message", "发送者ID不能为空"));
        }
        
        if (msg.getGroupId() == null || msg.getGroupId().trim().isEmpty()) {
            log.warn("收到无效消息：groupId为空, senderId={}", msg.getSenderUserId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "ERROR", "message", "群ID不能为空"));
        }

        log.info("收到群消息: {} 来自: {}, senderId={}", 
                msg.getContent(), msg.getGroupId(), msg.getSenderUserId());

        try {
            // 1. 调用 AI 分析
            TicketDraft draftResult = agentService.analyze(msg);

            // 处理重复消息的情况
            if (draftResult == null) {
                log.info("消息已被过滤（重复或无效）：senderId={}", msg.getSenderUserId());
                return ResponseEntity.ok(Map.of(
                        "status", "FILTERED",
                        "message", "消息已处理或为重复消息"
                ));
            }

            log.info("AI 分析结果: 可处理={}, 意图={}, 缺失信息={}, 建议回复={}, 置信度={}",
                    draftResult.isActionable(),
                    draftResult.getIntent(),
                    draftResult.getMissingInfo(),
                    draftResult.getSuggestedReply(),
                    draftResult.getConfidence());

            // 2. 逻辑分流处理
            if (draftResult.isActionable()) {
                // 情况 A：AI 认为报修要素齐全 (位置+描述都有了)
                try {
                    // 先保存草稿
                    TicketDraftEntity entity = new TicketDraftEntity();
                    entity.setGroupId(msg.getGroupId());
                    entity.setSenderId(msg.getSenderUserId());
                    entity.setContent(msg.getContent() != null ? msg.getContent() : "");
                    entity.setAiAnalysis(JSON.toJSONString(draftResult));
                    entity.setStatus(0); // 0-待处理
                    entity.setCreateTime(LocalDateTime.now());
                    draftService.save(entity);
                    log.info("【入库成功】AI 已识别有效报修，已存入草稿池, senderId={}", msg.getSenderUserId());

                    // 调用外部下单接口
                    OrderResponse orderResponse = callOrderService(draftResult, msg);
                    
                    // 构建响应
                    Map<String, Object> responseData = new java.util.HashMap<>();
                    responseData.put("status", "SAVED");
                    responseData.put("message", "已存入草稿池");
                    responseData.put("data", draftResult);
                    
                    // 如果下单成功，添加订单信息
                    if (orderResponse != null && Boolean.TRUE.equals(orderResponse.getSuccess())) {
                        responseData.put("orderId", orderResponse.getOrderId());
                        responseData.put("orderMessage", "下单成功：" + orderResponse.getMessage());
                        log.info("【下单成功】orderId={}, senderId={}", orderResponse.getOrderId(), msg.getSenderUserId());
                    } else if (orderResponse != null) {
                        // 下单失败但不影响草稿保存
                        responseData.put("orderMessage", "下单失败：" + (orderResponse.getErrorMessage() != null 
                                ? orderResponse.getErrorMessage() : orderResponse.getMessage()));
                        log.warn("【下单失败】senderId={}, error={}", msg.getSenderUserId(), 
                                orderResponse.getErrorMessage());
                    }

                    return ResponseEntity.ok(responseData);
                } catch (Exception e) {
                    log.error("保存工单草稿失败：senderId={}, error={}", msg.getSenderUserId(), e.getMessage(), e);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                            "status", "ERROR",
                            "message", "保存工单草稿失败：" + e.getMessage()
                    ));
                }
            } else {
                // 情况 B：AI 认为信息不全 (actionable=false)
                log.warn("【拦截入库】原因: {}, 建议追问: {}, senderId={}",
                        draftResult.getMissingInfo(), draftResult.getSuggestedReply(), msg.getSenderUserId());

                // 虽然不入库，但我们返回 202 (Accepted)，并告诉调用方 AI 的追问语
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                        "status", "NEED_MORE_INFO",
                        "missing", draftResult.getMissingInfo() != null ? draftResult.getMissingInfo() : "",
                        "reply", draftResult.getSuggestedReply() != null ? draftResult.getSuggestedReply() : "",
                        "data", draftResult
                ));
            }
        } catch (IllegalArgumentException e) {
            log.warn("参数错误：{}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("处理消息异常：senderId={}, error={}", msg.getSenderUserId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", "处理消息失败：" + e.getMessage()
            ));
        }
    }

    /**
     * 2. 供侧边栏调用的接口：获取当前群的 AI 建议
     */
    @GetMapping("/drafts")
    public ResponseEntity<List<TicketDraftEntity>> getGroupDrafts(@RequestParam String groupId) {
        if (groupId == null || groupId.trim().isEmpty()) {
            log.warn("查询工单草稿失败：groupId为空");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        
        try {
            List<TicketDraftEntity> drafts = draftService.list(new LambdaQueryWrapper<TicketDraftEntity>()
                    .eq(TicketDraftEntity::getGroupId, groupId)
                    .eq(TicketDraftEntity::getStatus, 0) // 只看待处理的
                    .orderByDesc(TicketDraftEntity::getCreateTime));
            log.debug("查询工单草稿成功：groupId={}, count={}", groupId, drafts.size());
            return ResponseEntity.ok(drafts);
        } catch (Exception e) {
            log.error("查询工单草稿异常：groupId={}, error={}", groupId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * 调用外部下单服务
     * @param draftResult AI分析结果
     * @param msg 原始消息
     * @return 下单响应
     */
    private OrderResponse callOrderService(TicketDraft draftResult, GroupMsgDTO msg) {
        try {
            // 构建下单请求
            OrderRequest orderRequest = OrderRequest.builder()
                    .intent(draftResult.getIntent())
                    .category(draftResult.getCategory())
                    .location(draftResult.getLocation())
                    .description(draftResult.getDescription())
                    .urgency(draftResult.getUrgency())
                    .senderId(draftResult.getSenderId())
                    .ownerName(draftResult.getOwnerName())
                    .roomNumber(draftResult.getRoomNumber())
                    .scheduledTime(draftResult.getScheduledTime())
                    .originalContent(msg.getContent())
                    .groupId(msg.getGroupId())
                    .build();
            
            // 调用下单服务
            return orderService.createOrder(orderRequest);
        } catch (Exception e) {
            log.error("调用下单服务异常：senderId={}, error={}", msg.getSenderUserId(), e.getMessage(), e);
            // 返回失败响应，但不抛出异常，保证草稿保存不受影响
            return OrderResponse.builder()
                    .success(false)
                    .errorCode("SERVICE_ERROR")
                    .errorMessage("调用下单服务异常：" + e.getMessage())
                    .build();
        }
    }
}
