package com.repair.aiops.controller;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.repair.aiops.model.dto.GroupMsgDTO;
import com.repair.aiops.model.dto.OrderRequest;
import com.repair.aiops.model.dto.OrderResponse;
import com.repair.aiops.model.dto.TicketDraft;
import com.repair.aiops.model.entity.TicketDraftEntity;
import com.repair.aiops.model.dto.wecom.WecomChatDataItem;
import com.repair.aiops.model.dto.wecom.WecomChatDataResponse;
import com.repair.aiops.model.dto.wecom.WecomChatFetchRequest;
import com.repair.aiops.service.business.ITicketDraftService;
import com.repair.aiops.service.client.IOrderService;
import com.repair.aiops.service.core.AgentService;
import com.repair.aiops.service.storage.OssStorageService;
import com.repair.aiops.service.wecom.WecomChatArchiveService;
import com.repair.aiops.service.wecom.WecomChatMessageParser;
import com.repair.aiops.service.wecom.WecomRobotService;
import com.repair.aiops.utils.WXBizMsgCrypt;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/wechat")
@Slf4j
public class AgentController {

    private final AgentService agentService;
    private final IOrderService orderService;

    @Autowired
    private com.repair.aiops.service.business.IOwnerService ownerService;

    @Autowired
    private ITicketDraftService draftService; // MyBatis-Plus Service

    @Autowired
    private WecomChatArchiveService wecomChatArchiveService;

    @Autowired
    private WecomChatMessageParser wecomChatMessageParser;

    @Autowired
    private OssStorageService ossStorageService;

    @Autowired
    private WecomRobotService wecomRobotService;

    @Value("${wecom.callback.token:}")
    private String callbackToken;

    @Value("${wecom.callback.aes-key:}")
    private String callbackAesKey;

    @Value("${wecom.corp-id:}")
    private String corpId;

    @Value("${wecom.chat.archive.allowed-groups:}")
    private String allowedGroups;

    public AgentController(AgentService agentService, IOrderService orderService) {
        this.agentService = agentService;
        this.orderService = orderService;
    }

    /**
     * 企业微信图片获取（通过 sdkfileid 拉取原始图片）
     */
    @GetMapping("/media/{sdkfileid}")
    public ResponseEntity<byte[]> fetchWecomMedia(@PathVariable("sdkfileid") String sdkFileId) {
        ResponseEntity<byte[]> response = wecomChatArchiveService.fetchMedia(sdkFileId);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return ResponseEntity.status(response.getStatusCode()).build();
        }
        // 透传内容类型，默认为二进制
        org.springframework.http.MediaType contentType = response.getHeaders().getContentType();
        return ResponseEntity.ok()
                .contentType(contentType != null ? contentType : org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .body(response.getBody());
    }

    /**
     * 企业微信回调 URL 验证
     */
    @GetMapping("/webhook")
    public ResponseEntity<String> verifyUrl(
            @RequestParam(name = "msg_signature") String msgSignature,
            @RequestParam(name = "timestamp") String timestamp,
            @RequestParam(name = "nonce") String nonce,
            @RequestParam(name = "echostr") String echostr) {
        
        log.info("收到企业微信验证请求: msg_signature={}, timestamp={}, nonce={}, echostr={}", 
                msgSignature, timestamp, nonce, echostr);
        
        try {
            if (callbackToken == null || callbackToken.isEmpty() || 
                callbackAesKey == null || callbackAesKey.isEmpty() || 
                corpId == null || corpId.isEmpty()) {
                log.error("验证失败：Token, AESKey 或 CorpID 未配置");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("配置缺失");
            }
            
            WXBizMsgCrypt wxcpt = new WXBizMsgCrypt(callbackToken, callbackAesKey, corpId);
            String result = wxcpt.verifyUrl(msgSignature, timestamp, nonce, echostr);
            
            log.info("验证成功，解密内容: {}", result);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("验证失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("验证失败");
        }
    }

    /**
     * 企业微信会话存档拉取入口（根据 seq 拉取会话记录并进行 AI 分析）
     * 注意：需要配置企业微信的会话存档密钥与解密流程，否则只能拿到加密内容
     */
    @PostMapping("/archive/fetch")
    public ResponseEntity<?> onWecomChatArchive(@RequestBody(required = false) WecomChatFetchRequest request) {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        MDC.put("traceId", traceId);

        Long seq = request != null ? request.getSeq() : 0L;
        Integer limit = request != null ? request.getLimit() : 50;

        log.info("[traceId={}] [开始] 企业微信会话存档拉取: seq={}, limit={}", traceId, seq, limit);
        long startTime = System.currentTimeMillis();

        WecomChatDataResponse response = wecomChatArchiveService.fetchChatData(seq, limit);
        if (response == null) {
            log.error("[traceId={}] [失败] 会话存档拉取失败: 可能因配置未启用或网络异常", traceId);
            MDC.remove("traceId");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "status", "ERROR",
                    "message", "会话存档拉取失败或未启用",
                    "traceId", traceId
            ));
        }

        List<WecomChatDataItem> chatData = response.getChatdata();
        int total = chatData != null ? chatData.size() : 0;
        log.info("[traceId={}] [拉取成功] 获取到 {} 条消息", traceId, total);

        int analyzed = 0;
        int skipped = 0;
        List<Map<String, Object>> results = new ArrayList<>();

        if (chatData != null) {
            for (WecomChatDataItem item : chatData) {
                String decrypted = item.getDecryptChatMsg();
                if (decrypted == null || decrypted.trim().isEmpty()) {
                    log.warn("[traceId={}] [跳过] 消息解密为空或无内容: seq={}", traceId, item.getSeq());
                    skipped++;
                    continue;
                }
                GroupMsgDTO msg = wecomChatMessageParser.parse(decrypted);
                if (msg == null) {
                    log.warn("[traceId={}] [跳过] 消息解析失败: seq={}, content={}", traceId, item.getSeq(), decrypted);
                    skipped++;
                    continue;
                }

                // 过滤群ID (白名单机制)
                if (allowedGroups != null && !allowedGroups.trim().isEmpty()) {
                    String[] groups = allowedGroups.split(",");
                    boolean allowed = false;
                    for (String g : groups) {
                        if (g.trim().equals(msg.getGroupId())) {
                            allowed = true;
                            break;
                        }
                    }
                    if (!allowed) {
                        // 仅调试级别打印，避免日志刷屏
                        log.debug("[traceId={}] [过滤] 群不在白名单内，跳过: groupId={}", traceId, msg.getGroupId());
                        skipped++;
                        continue;
                    }
                }

                // 图片处理：如果是 sdkfileid，则拉取并上传 OSS
                if (msg.getImageUrl() != null && !msg.getImageUrl().isEmpty()) {
                    String sdkFileId = extractSdkFileId(msg.getImageUrl());
                    if (sdkFileId != null) {
                        log.info("[traceId={}] [图片处理] 开始下载图片: sdkFileId={}", traceId, sdkFileId);
                        ResponseEntity<byte[]> media = wecomChatArchiveService.fetchMedia(sdkFileId);
                        if (media.getStatusCode().is2xxSuccessful() && media.getBody() != null) {
                            String contentType = media.getHeaders().getContentType() != null
                                    ? media.getHeaders().getContentType().toString()
                                    : "image/jpeg";
                            String ossUrl = ossStorageService.upload(media.getBody(), contentType);
                            if (ossUrl != null) {
                                log.info("[traceId={}] [图片处理] 上传OSS成功: url={}", traceId, ossUrl);
                                msg.setImageUrl(ossUrl);
                            } else {
                                log.error("[traceId={}] [图片处理] 上传OSS失败", traceId);
                            }
                        } else {
                            log.error("[traceId={}] [图片处理] 下载图片失败: status={}", traceId, media.getStatusCode());
                        }
                    }
                }

                log.info("[traceId={}] [开始分析] 处理单条消息: seq={}, senderId={}", traceId, item.getSeq(), msg.getSenderUserId());
                ResponseEntity<?> analyzeResult = onGroupMessage(msg);

                Map<String, Object> result = new HashMap<>();
                result.put("seq", item.getSeq());
                result.put("senderUserId", msg.getSenderUserId());
                result.put("groupId", msg.getGroupId());
                result.put("httpStatus", analyzeResult.getStatusCode().value());
                result.put("body", analyzeResult.getBody());
                results.add(result);
                analyzed++;
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("[traceId={}] [结束] 本次任务完成: total={}, analyzed={}, skipped={}, nextSeq={}, duration={}ms",
                traceId, total, analyzed, skipped, response.getNext_seq(), duration);

        MDC.remove("traceId");
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "traceId", traceId,
                "nextSeq", response.getNext_seq(),
                "total", total,
                "analyzed", analyzed,
                "skipped", skipped,
                "durationMs", duration,
                "results", results
        ));
    }

    private String extractSdkFileId(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return null;
        }
        if (!imageUrl.startsWith("http")) {
            return imageUrl;
        }
        int idx = imageUrl.lastIndexOf('/');
        if (idx >= 0 && idx + 1 < imageUrl.length()) {
            return imageUrl.substring(idx + 1);
        }
        return null;
    }


    @PostMapping("/webhook")
    public ResponseEntity<?> onGroupMessage(@RequestBody GroupMsgDTO msg) {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        MDC.put("traceId", traceId);
        // 参数校验
        if (msg == null) {
            log.warn("[traceId={}] 收到空消息请求", traceId);
            MDC.remove("traceId");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "ERROR", "message", "消息不能为空"));
        }

        if (msg.getSenderUserId() == null || msg.getSenderUserId().trim().isEmpty()) {
            log.warn("[traceId={}] 收到无效消息：senderUserId为空", traceId);
            MDC.remove("traceId");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "ERROR", "message", "发送者ID不能为空"));
        }

        if (msg.getGroupId() == null || msg.getGroupId().trim().isEmpty()) {
            log.warn("[traceId={}] 收到无效消息：groupId为空, senderId={}", traceId, msg.getSenderUserId());
            MDC.remove("traceId");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "ERROR", "message", "群ID不能为空"));
        }

        log.info("[traceId={}] 收到群消息: content={}, groupId={}, senderId={}, hasImage={}",
                traceId,
                msg.getContent(),
                msg.getGroupId(),
                msg.getSenderUserId(),
                msg.getImageUrl() != null && !msg.getImageUrl().isEmpty());

        try {
            // 1. 调用 AI 分析
            TicketDraft draftResult = agentService.analyze(msg);

            // 处理重复消息的情况
            if (draftResult == null) {
                log.info("[traceId={}] 消息已被过滤（重复或无效）：senderId={}", traceId, msg.getSenderUserId());
                return ResponseEntity.ok(Map.of(
                        "status", "FILTERED",
                        "message", "消息已处理或为重复消息"
                ));
            }

            log.info("[traceId={}] AI 分析结果: 可处理={}, 意图={}, 缺失信息={}, 建议回复={}, 置信度={}",
                    traceId,
                    draftResult.isActionable(),
                    draftResult.getIntent(),
                    draftResult.getMissingInfo(),
                    draftResult.getSuggestedReply(),
                    draftResult.getConfidence());

            // 2. 逻辑分流处理
            if (draftResult.isActionable()) {
                // 情况 A：AI 认为报修要素齐全 (位置+描述都有了)
                log.info("[traceId={}] [决策] 信息完整，准备下单: intent={}", traceId, draftResult.getIntent());
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
                    log.info("[traceId={}] [入库] 草稿保存成功: id={}", traceId, entity.getId());

                    // 调用外部下单接口
                    log.info("[traceId={}] [调用] 开始调用外部下单接口...", traceId);
                    long callStart = System.currentTimeMillis();
                    OrderResponse orderResponse = callOrderService(draftResult, msg);
                    long callDuration = System.currentTimeMillis() - callStart;

                    // 构建响应
                    Map<String, Object> responseData = new java.util.HashMap<>();
                    responseData.put("status", "SAVED");
                    responseData.put("message", "已存入草稿池");
                    responseData.put("data", draftResult);

                    // 如果下单成功，添加订单信息
                    if (orderResponse != null && Boolean.TRUE.equals(orderResponse.getSuccess())) {
                        responseData.put("orderId", orderResponse.getOrderId());
                        responseData.put("orderMessage", "下单成功：" + orderResponse.getMessage());
                        log.info("[traceId={}] [调用成功] 下单完成: orderId={}, duration={}ms, message={}",
                                traceId, orderResponse.getOrderId(), callDuration, orderResponse.getMessage());
                    } else if (orderResponse != null) {
                        // 下单失败但不影响草稿保存
                        responseData.put("orderMessage", "下单失败：" + (orderResponse.getErrorMessage() != null
                                ? orderResponse.getErrorMessage() : orderResponse.getMessage()));
                        log.warn("[traceId={}] [调用失败] 下单接口返回错误: duration={}ms, error={}",
                                traceId, callDuration, orderResponse.getErrorMessage());
                    } else {
                         log.error("[traceId={}] [调用异常] 下单接口返回空响应: duration={}ms", traceId, callDuration);
                    }

                    return ResponseEntity.ok(responseData);
                } catch (Exception e) {
                    log.error("[traceId={}] 保存工单草稿失败：senderId={}, error={}",
                            traceId, msg.getSenderUserId(), e.getMessage(), e);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                            "status", "ERROR",
                            "message", "保存工单草稿失败：" + e.getMessage()
                    ));
                }
            } else {
                // 情况 B：AI 认为信息不全 (actionable=false)
                String missingInfoStr = draftResult.getMissingInfo() != null ? String.join(",", draftResult.getMissingInfo()) : "";
                log.warn("[traceId={}] [拦截] 信息不全，拦截入库: missing={}, reply={}",
                        traceId, missingInfoStr, draftResult.getSuggestedReply());

                // 通知群机器人，提示缺失信息
                try {
                    wecomRobotService.sendMissingInfoNotice(
                            traceId,
                            msg.getGroupId(),
                            msg.getSenderUserId(),
                            missingInfoStr,
                            draftResult.getSuggestedReply()
                    );
                    log.info("[traceId={}] [通知] 机器人通知发送成功", traceId);
                } catch (Exception e) {
                    log.error("[traceId={}] [通知] 机器人通知发送失败: error={}", traceId, e.getMessage());
                }

                // 虽然不入库，但我们返回 202 (Accepted)，并告诉调用方 AI 的追问语
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                        "status", "NEED_MORE_INFO",
                        "missing", missingInfoStr,
                        "reply", draftResult.getSuggestedReply() != null ? draftResult.getSuggestedReply() : "",
                        "data", draftResult
                ));
            }
        } catch (IllegalArgumentException e) {
            log.warn("[traceId={}] 参数错误：{}", traceId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("[traceId={}] 处理消息异常：senderId={}, error={}",
                    traceId, msg.getSenderUserId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", "处理消息失败：" + e.getMessage()
            ));
        } finally {
            MDC.remove("traceId");
        }
    }

    /**
     * 2. 供侧边栏调用的接口：获取当前群的 AI 建议
     */
    @GetMapping("/drafts")
    public ResponseEntity<List<TicketDraftEntity>> getGroupDrafts(@RequestParam(required = false) String groupId) {
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
            // 获取房屋ID (假设Owner的ID即为houseId，或者需要另外的映射逻辑)
            Long houseId = 918L; // 默认值，防止空指针
            try {
                com.repair.aiops.model.entity.Owner owner = ownerService.getOne(
                        new LambdaQueryWrapper<com.repair.aiops.model.entity.Owner>()
                        .eq(com.repair.aiops.model.entity.Owner::getSenderId, draftResult.getSenderId()));
                if (owner != null) {
                    houseId = owner.getId();
                }
            } catch (Exception e) {
                log.warn("获取房屋ID失败，使用默认值：senderId={}", draftResult.getSenderId());
            }

            // 处理图片
            java.util.List<String> fileList = new java.util.ArrayList<>();
            if (msg.getImageUrl() != null && !msg.getImageUrl().isEmpty()) {
                fileList.add(msg.getImageUrl());
            }

            // 处理预约时间 (解析 scheduledTime 或使用默认值)
            String startTime = "2026-01-21 08:00:00";
            String endTime = "2026-01-21 09:00:00";
            // TODO: 解析 draftResult.getScheduledTime()

            log.info("[traceId={}] 下单参数预览: senderId={}, houseId={}, fileCount={}, startTime={}, endTime={}",
                    MDC.get("traceId"),
                    draftResult.getSenderId(),
                    houseId,
                    fileList.size(),
                    startTime,
                    endTime);

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
                    // 新增字段
                    .houseId(houseId)
                    .fileList(fileList)
                    .appointmentStartTime(startTime)
                    .appointmentEndTime(endTime)
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
