# API测试说明

## 应用状态

应用应该在运行在：`http://localhost:8080`

如果8080端口被占用，说明应用可能已经在运行。

## 测试方法

### 方法1：使用Apipost/Postman（推荐）

#### 接口1：接收群消息
- **URL**: `POST http://localhost:8080/api/wechat/webhook`
- **Headers**: 
  ```
  Content-Type: application/json
  ```
- **Body** (选择raw JSON):

**测试用例1：完整报修消息**
```json
{
  "senderUserId": "user001",
  "groupId": "group001",
  "content": "我家3-201的灯坏了，需要维修",
  "timestamp": 1705046400000
}
```

**测试用例2：信息不全（只有描述）**
```json
{
  "senderUserId": "user002",
  "groupId": "group001",
  "content": "灯坏了",
  "timestamp": 1705046400000
}
```

**测试用例3：投诉类型**
```json
{
  "senderUserId": "user003",
  "groupId": "group001",
  "content": "3-201的楼上太吵了，影响休息",
  "timestamp": 1705046400000
}
```

**测试用例4：咨询类型**
```json
{
  "senderUserId": "user004",
  "groupId": "group001",
  "content": "物业费什么时候交？",
  "timestamp": 1705046400000
}
```

**测试用例5：闲聊消息**
```json
{
  "senderUserId": "user005",
  "groupId": "group001",
  "content": "收到，谢谢",
  "timestamp": 1705046400000
}
```

**测试用例6：带图片**
```json
{
  "senderUserId": "user006",
  "groupId": "group001",
  "content": "3-201的卫生间漏水了",
  "imageUrl": "https://example.com/image.jpg",
  "timestamp": 1705046400000
}
```

**测试用例7：参数校验（缺少senderUserId）**
```json
{
  "groupId": "group001",
  "content": "测试消息"
}
```

**预期结果：HTTP 400 Bad Request**

#### 接口2：查询草稿
- **URL**: `GET http://localhost:8080/api/wechat/drafts?groupId=group001`
- **Headers**: 无特殊要求

### 方法2：使用PowerShell命令

```powershell
# 测试完整报修消息
$body = '{"senderUserId":"user001","groupId":"group001","content":"我家3-201的灯坏了，需要维修","timestamp":1705046400000}'
Invoke-RestMethod -Uri "http://localhost:8080/api/wechat/webhook" -Method POST -Body $body -ContentType "application/json"

# 测试信息不全
$body2 = '{"senderUserId":"user002","groupId":"group001","content":"灯坏了","timestamp":1705046400000}'
Invoke-RestMethod -Uri "http://localhost:8080/api/wechat/webhook" -Method POST -Body $body2 -ContentType "application/json"

# 查询草稿
Invoke-RestMethod -Uri "http://localhost:8080/api/wechat/drafts?groupId=group001" -Method GET
```

### 方法3：使用curl（如果安装了curl）

```bash
# 测试完整报修消息
curl -X POST "http://localhost:8080/api/wechat/webhook" \
  -H "Content-Type: application/json" \
  -d '{"senderUserId":"user001","groupId":"group001","content":"我家3-201的灯坏了，需要维修","timestamp":1705046400000}'

# 查询草稿
curl -X GET "http://localhost:8080/api/wechat/drafts?groupId=group001"
```

## 预期响应

### 成功响应（信息完整，actionable=true）
```json
{
  "status": "SAVED",
  "message": "已存入草稿池",
  "data": {
    "actionable": true,
    "intent": "REPAIR",
    "category": "水电",
    "location": "3-201",
    "description": "灯坏了",
    "urgency": "MEDIUM",
    ...
  }
}
```

### 信息不全响应（actionable=false）
```json
{
  "status": "NEED_MORE_INFO",
  "missing": "缺少具体位置信息",
  "reply": "好的收到，请问具体的报修位置是在哪里？",
  "data": {
    "actionable": false,
    "intent": "REPAIR",
    "missingInfo": "缺少具体位置信息",
    ...
  }
}
```

### 参数错误响应
```json
{
  "status": "ERROR",
  "message": "发送者ID不能为空"
}
```

## 检查点

1. ✅ 接口能正常接收请求
2. ✅ 参数校验正常工作
3. ✅ AI分析结果正确
4. ✅ 完整信息保存到数据库
5. ✅ 不完整信息不入库
6. ✅ 查询草稿接口正常

## 注意事项

- 如果AI API调用失败，检查通义千问API Key配置
- 如果数据库保存失败，检查数据库连接
- 如果Redis连接失败，检查Redis配置
- 查看应用控制台日志获取详细信息
