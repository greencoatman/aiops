#!/bin/bash
# API测试脚本 (Linux/Mac/Git Bash)
# 使用方法：应用启动后，运行 bash test_api_curl.sh

BASE_URL="http://localhost:8080/api/wechat"

echo "========================================="
echo "AIOps API 测试脚本"
echo "========================================="
echo ""

# 获取当前时间戳（毫秒）
TIMESTAMP=$(date +%s)000

# 测试1：完整报修消息（有位置和描述）
echo "测试1: 完整报修消息（有位置和描述）"
curl -X POST "$BASE_URL/webhook" \
  -H "Content-Type: application/json" \
  -d "{
    \"senderUserId\": \"user001\",
    \"groupId\": \"group001\",
    \"content\": \"我家3-201的灯坏了，需要维修\",
    \"timestamp\": $TIMESTAMP
  }" | jq '.'
echo ""
sleep 2

# 测试2：只有描述，没有位置（信息不全）
echo "测试2: 只有描述，没有位置（信息不全）"
TIMESTAMP=$(date +%s)000
curl -X POST "$BASE_URL/webhook" \
  -H "Content-Type: application/json" \
  -d "{
    \"senderUserId\": \"user002\",
    \"groupId\": \"group001\",
    \"content\": \"灯坏了\",
    \"timestamp\": $TIMESTAMP
  }" | jq '.'
echo ""
sleep 2

# 测试3：投诉类型
echo "测试3: 投诉类型消息"
TIMESTAMP=$(date +%s)000
curl -X POST "$BASE_URL/webhook" \
  -H "Content-Type: application/json" \
  -d "{
    \"senderUserId\": \"user003\",
    \"groupId\": \"group001\",
    \"content\": \"3-201的楼上太吵了，影响休息\",
    \"timestamp\": $TIMESTAMP
  }" | jq '.'
echo ""
sleep 2

# 测试4：咨询类型
echo "测试4: 咨询类型消息"
TIMESTAMP=$(date +%s)000
curl -X POST "$BASE_URL/webhook" \
  -H "Content-Type: application/json" \
  -d "{
    \"senderUserId\": \"user004\",
    \"groupId\": \"group001\",
    \"content\": \"物业费什么时候交？\",
    \"timestamp\": $TIMESTAMP
  }" | jq '.'
echo ""
sleep 2

# 测试5：闲聊消息（应该被过滤）
echo "测试5: 闲聊消息（应该被过滤）"
TIMESTAMP=$(date +%s)000
curl -X POST "$BASE_URL/webhook" \
  -H "Content-Type: application/json" \
  -d "{
    \"senderUserId\": \"user005\",
    \"groupId\": \"group001\",
    \"content\": \"收到，谢谢\",
    \"timestamp\": $TIMESTAMP
  }" | jq '.'
echo ""
sleep 2

# 测试6：带图片的消息
echo "测试6: 带图片URL的消息"
TIMESTAMP=$(date +%s)000
curl -X POST "$BASE_URL/webhook" \
  -H "Content-Type: application/json" \
  -d "{
    \"senderUserId\": \"user006\",
    \"groupId\": \"group001\",
    \"content\": \"3-201的卫生间漏水了\",
    \"imageUrl\": \"https://example.com/image.jpg\",
    \"timestamp\": $TIMESTAMP
  }" | jq '.'
echo ""
sleep 2

# 测试7：多轮对话测试（先发不完整信息，再补全）
echo "测试7: 多轮对话测试（先发不完整信息）"
TIMESTAMP=$(date +%s)000
curl -X POST "$BASE_URL/webhook" \
  -H "Content-Type: application/json" \
  -d "{
    \"senderUserId\": \"user007\",
    \"groupId\": \"group001\",
    \"content\": \"灯坏了\",
    \"timestamp\": $TIMESTAMP
  }" | jq '.'
echo ""

sleep 2

echo "测试7: 多轮对话测试（补全位置信息）"
TIMESTAMP=$(date +%s)000
curl -X POST "$BASE_URL/webhook" \
  -H "Content-Type: application/json" \
  -d "{
    \"senderUserId\": \"user007\",
    \"groupId\": \"group001\",
    \"content\": \"在3-201的客厅\",
    \"timestamp\": $TIMESTAMP
  }" | jq '.'
echo ""

# 测试8：参数校验测试（缺少senderUserId）
echo "测试8: 参数校验测试（缺少senderUserId）"
curl -X POST "$BASE_URL/webhook" \
  -H "Content-Type: application/json" \
  -d "{
    \"groupId\": \"group001\",
    \"content\": \"测试消息\"
  }" | jq '.'
echo ""

# 测试9：查询草稿接口
echo "测试9: 查询草稿接口"
curl -X GET "$BASE_URL/drafts?groupId=group001" | jq '.'
echo ""

echo "========================================="
echo "测试完成！"
echo "========================================="
