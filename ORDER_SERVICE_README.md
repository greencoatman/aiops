# 外部下单服务集成说明

## 功能概述

当AI识别到有效的报修需求（`actionable=true`）时，系统会自动调用外部系统的下单接口创建工单。

## 技术方案

**推荐使用：RestTemplate（已实现）**

- ✅ 项目已有RestTemplate配置
- ✅ 同步调用，适合下单场景
- ✅ 简单直接，易于维护
- ✅ 已配置超时（连接5秒，读取10秒）

## 配置说明

在 `application.properties` 中添加以下配置：

```properties
# 外部下单API配置
# 是否启用自动下单（true=启用，false=只保存草稿不调用下单接口）
aiops.order.enabled=false

# 外部系统下单API的URL（请根据实际环境修改）
aiops.order.api-url=http://localhost:8080/api/orders

# 外部系统API的认证Token（如果需要认证，请配置此值）
aiops.order.api-token=your-token-here
```

## 接口规范

### 请求格式（OrderRequest）

外部系统需要接收的请求格式：

```json
{
  "intent": "REPAIR",           // 意图：REPAIR/COMPLAINT/INQUIRY
  "category": "水电",            // 问题分类
  "location": "3-201",          // 位置信息
  "description": "灯坏了",       // 问题描述
  "urgency": "MEDIUM",          // 紧急程度：HIGH/MEDIUM/LOW
  "senderId": "user123",        // 发送者ID
  "ownerName": "张三",           // 业主姓名
  "roomNumber": "3-201",        // 房号
  "scheduledTime": "明天下午",   // 预约时间（可选）
  "originalContent": "我家灯坏了", // 原始消息内容
  "groupId": "group123"         // 群ID
}
```

### 响应格式（OrderResponse）

外部系统需要返回的响应格式：

**成功响应：**
```json
{
  "success": true,
  "orderId": "ORDER20231201001",
  "message": "下单成功"
}
```

**失败响应：**
```json
{
  "success": false,
  "errorCode": "INVALID_PARAM",
  "errorMessage": "参数错误：位置信息不能为空"
}
```

## 工作流程

1. **AI识别报修需求**
   - 用户发送消息 → AI分析 → 识别为有效报修（actionable=true）

2. **保存草稿**
   - 先保存到数据库（work_order_drafts表）
   - 状态设为0（待处理）

3. **调用下单接口**
   - 将TicketDraft转换为OrderRequest
   - 调用外部系统的下单API
   - 如果配置了`aiops.order.enabled=false`，则跳过此步骤

4. **处理响应**
   - 下单成功：返回订单号，记录日志
   - 下单失败：记录错误日志，但不影响草稿保存

## 代码结构

```
src/main/java/com/repair/aiops/
├── model/dto/
│   ├── OrderRequest.java      # 下单请求DTO
│   └── OrderResponse.java     # 下单响应DTO
├── service/client/
│   ├── IOrderService.java     # 下单服务接口
│   └── impl/
│       └── OrderServiceImpl.java  # 下单服务实现（使用RestTemplate）
└── controller/
    └── AgentController.java   # 集成下单服务调用
```

## 使用示例

### 1. 启用自动下单

```properties
# application.properties
aiops.order.enabled=true
aiops.order.api-url=http://your-order-system.com/api/orders
aiops.order.api-token=your-api-token
```

### 2. 查看调用日志

下单成功日志：
```
[INFO] 调用外部下单接口：url=http://..., request=...
[INFO] 下单成功：orderId=ORDER20231201001, senderId=user123
[INFO] 【下单成功】orderId=ORDER20231201001, senderId=user123
```

下单失败日志：
```
[WARN] 下单失败：response=..., senderId=user123
[WARN] 【下单失败】senderId=user123, error=参数错误
```

### 3. 响应示例

**成功响应：**
```json
{
  "status": "SAVED",
  "message": "已存入草稿池",
  "orderId": "ORDER20231201001",
  "orderMessage": "下单成功：订单创建成功",
  "data": { /* TicketDraft数据 */ }
}
```

**失败响应（下单失败但草稿已保存）：**
```json
{
  "status": "SAVED",
  "message": "已存入草稿池",
  "orderMessage": "下单失败：外部系统连接超时",
  "data": { /* TicketDraft数据 */ }
}
```

## 注意事项

1. **容错设计**
   - 下单失败不影响草稿保存
   - 外部系统异常不会导致整个流程失败
   - 所有异常都会记录日志

2. **超时设置**
   - 连接超时：5秒
   - 读取超时：10秒
   - 可根据实际情况调整

3. **认证方式**
   - 当前支持Bearer Token认证
   - 如需其他认证方式，可修改`OrderServiceImpl.java`

4. **开关控制**
   - 通过`aiops.order.enabled`控制是否调用下单接口
   - 设置为`false`时，只保存草稿，不调用下单接口
   - 适合测试环境或分阶段上线

## 扩展建议

如果需要更高级的功能，可以考虑：

1. **异步调用**
   - 使用`@Async`注解，避免阻塞主流程
   - 使用消息队列（RabbitMQ/Kafka）异步处理

2. **重试机制**
   - 使用Spring Retry实现自动重试
   - 配置重试次数和重试间隔

3. **断路器**
   - 使用Resilience4j或Hystrix
   - 防止外部系统故障影响主流程

4. **监控告警**
   - 集成Prometheus监控下单成功率
   - 设置告警规则
