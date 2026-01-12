# API测试指南

## 准备工作

1. **启动应用**
   ```bash
   mvn spring-boot:run
   ```
   或使用IDE运行 `AiopsApplication`

2. **确认服务启动**
   - 应用默认运行在：`http://localhost:8080`
   - 查看启动日志确认无错误

## 测试方式

### 方式1：使用PowerShell脚本（Windows推荐）

```powershell
# 在PowerShell中运行
.\test_api.ps1
```

### 方式2：使用curl脚本（Linux/Mac/Git Bash）

```bash
bash test_api_curl.sh
```

### 方式3：使用Apipost/Postman

#### 接口1：接收群消息（Webhook）

**URL:** `POST http://localhost:8080/api/wechat/webhook`

**Headers:**
```
Content-Type: application/json
```

**测试用例1：完整报修消息（有位置和描述）**
```json
{
  "senderUserId": "user001",
  "groupId": "group001",
  "content": "我家3-201的灯坏了，需要维修",
  "timestamp": 1705046400000
}
```

**预期结果：**
- `status`: "SAVED"
- `actionable`: true
- 应该保存到数据库

---

**测试用例2：只有描述，没有位置（信息不全）**
```json
{
  "senderUserId": "user002",
  "groupId": "group001",
  "content": "灯坏了",
  "timestamp": 1705046400000
}
```

**预期结果：**
- `status`: "NEED_MORE_INFO"
- `actionable`: false
- `missingInfo`: 应该包含"缺少位置信息"
- `suggestedReply`: 应该有追问建议

---

**测试用例3：投诉类型消息**
```json
{
  "senderUserId": "user003",
  "groupId": "group001",
  "content": "3-201的楼上太吵了，影响休息",
  "timestamp": 1705046400000
}
```

**预期结果：**
- `intent`: "COMPLAINT"
- `actionable`: true（投诉也可以有位置）

---

**测试用例4：咨询类型消息**
```json
{
  "senderUserId": "user004",
  "groupId": "group001",
  "content": "物业费什么时候交？",
  "timestamp": 1705046400000
}
```

**预期结果：**
- `intent`: "INQUIRY"
- `actionable`: true

---

**测试用例5：闲聊消息（应该被过滤）**
```json
{
  "senderUserId": "user005",
  "groupId": "group001",
  "content": "收到，谢谢",
  "timestamp": 1705046400000
}
```

**预期结果：**
- `intent`: "NOISE"
- `actionable`: false
- 不入库

---

**测试用例6：带图片的消息**
```json
{
  "senderUserId": "user006",
  "groupId": "group001",
  "content": "3-201的卫生间漏水了",
  "imageUrl": "https://example.com/image.jpg",
  "timestamp": 1705046400000
}
```

**预期结果：**
- 应该处理图片URL
- AI会分析图片内容

---

**测试用例7：多轮对话测试**

**第一步：发送不完整信息**
```json
{
  "senderUserId": "user007",
  "groupId": "group001",
  "content": "灯坏了",
  "timestamp": 1705046400000
}
```

**第二步：补全位置信息（30秒内）**
```json
{
  "senderUserId": "user007",
  "groupId": "group001",
  "content": "在3-201的客厅",
  "timestamp": 1705046700000
}
```

**预期结果：**
- 第一步：`actionable`: false, `status`: "NEED_MORE_INFO"
- 第二步：AI应该结合历史记忆，`actionable`: true, `status`: "SAVED"

---

**测试用例8：参数校验测试（缺少senderUserId）**
```json
{
  "groupId": "group001",
  "content": "测试消息"
}
```

**预期结果：**
- HTTP 400 Bad Request
- `message`: "发送者ID不能为空"

---

**测试用例9：参数校验测试（缺少groupId）**
```json
{
  "senderUserId": "user008",
  "content": "测试消息"
}
```

**预期结果：**
- HTTP 400 Bad Request
- `message`: "群ID不能为空"

---

#### 接口2：查询草稿（Drafts）

**URL:** `GET http://localhost:8080/api/wechat/drafts?groupId=group001`

**预期结果：**
- 返回该群组的待处理工单草稿列表
- 按创建时间倒序排列

---

## 测试检查点

### 1. 基本功能检查
- ✅ 接口能正常接收请求
- ✅ 参数校验正常工作
- ✅ 异常处理正常

### 2. AI分析检查
- ✅ 意图识别是否正确（报修/投诉/咨询/闲聊）
- ✅ 位置信息提取是否正确
- ✅ 紧急程度判断是否合理
- ✅ 信息完整性判断是否正确

### 3. 业务逻辑检查
- ✅ 完整信息是否入库（actionable=true）
- ✅ 不完整信息是否不入库（actionable=false）
- ✅ 多轮对话记忆是否生效
- ✅ 消息去重是否生效

### 4. 数据检查
- ✅ 数据库是否正确保存
- ✅ Redis记忆是否正确存储
- ✅ 日志记录是否完整

## 常见问题

### 1. 应用启动失败
- 检查数据库连接配置
- 检查Redis连接配置
- 检查AI API Key配置

### 2. AI分析返回错误
- 检查通义千问API Key是否有效
- 检查网络连接
- 查看应用日志

### 3. 数据库保存失败
- 检查数据库连接
- 检查表结构是否正确
- 查看应用日志

### 4. Redis连接失败
- 检查Redis服务是否运行
- 检查Redis连接配置
- 查看应用日志

## 日志查看

应用日志会输出到控制台，关键日志包括：
- `收到群消息:` - 接收到的消息
- `AI 分析结果:` - AI分析结果
- `【入库成功】` - 成功保存草稿
- `【拦截入库】` - 信息不全，不入库
- `【下单成功】` - 下单成功（如果启用）
- `【下单失败】` - 下单失败（如果启用）
