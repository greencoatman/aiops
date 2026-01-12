# API测试脚本
# 使用方法：应用启动后，在PowerShell中运行此脚本

$baseUrl = "http://localhost:8080/api/wechat"

Write-Host "=========================================" -ForegroundColor Green
Write-Host "AIOps API 测试脚本" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green
Write-Host ""

# 测试1：完整报修消息（有位置和描述）
Write-Host "测试1: 完整报修消息（有位置和描述）" -ForegroundColor Yellow
$test1 = @{
    senderUserId = "user001"
    groupId = "group001"
    content = "我家3-201的灯坏了，需要维修"
    timestamp = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
} | ConvertTo-Json

$response1 = Invoke-RestMethod -Uri "$baseUrl/webhook" -Method POST -Body $test1 -ContentType "application/json; charset=utf-8"
Write-Host "响应：" -ForegroundColor Cyan
$response1 | ConvertTo-Json -Depth 10
Write-Host ""
Start-Sleep -Seconds 2

# 测试2：只有描述，没有位置（信息不全）
Write-Host "测试2: 只有描述，没有位置（信息不全）" -ForegroundColor Yellow
$test2 = @{
    senderUserId = "user002"
    groupId = "group001"
    content = "灯坏了"
    timestamp = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
} | ConvertTo-Json

$response2 = Invoke-RestMethod -Uri "$baseUrl/webhook" -Method POST -Body $test2 -ContentType "application/json; charset=utf-8"
Write-Host "响应：" -ForegroundColor Cyan
$response2 | ConvertTo-Json -Depth 10
Write-Host ""
Start-Sleep -Seconds 2

# 测试3：投诉类型
Write-Host "测试3: 投诉类型消息" -ForegroundColor Yellow
$test3 = @{
    senderUserId = "user003"
    groupId = "group001"
    content = "3-201的楼上太吵了，影响休息"
    timestamp = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
} | ConvertTo-Json

$response3 = Invoke-RestMethod -Uri "$baseUrl/webhook" -Method POST -Body $test3 -ContentType "application/json; charset=utf-8"
Write-Host "响应：" -ForegroundColor Cyan
$response3 | ConvertTo-Json -Depth 10
Write-Host ""
Start-Sleep -Seconds 2

# 测试4：咨询类型
Write-Host "测试4: 咨询类型消息" -ForegroundColor Yellow
$test4 = @{
    senderUserId = "user004"
    groupId = "group001"
    content = "物业费什么时候交？"
    timestamp = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
} | ConvertTo-Json

$response4 = Invoke-RestMethod -Uri "$baseUrl/webhook" -Method POST -Body $test4 -ContentType "application/json; charset=utf-8"
Write-Host "响应：" -ForegroundColor Cyan
$response4 | ConvertTo-Json -Depth 10
Write-Host ""
Start-Sleep -Seconds 2

# 测试5：闲聊消息（应该被过滤）
Write-Host "测试5: 闲聊消息（应该被过滤）" -ForegroundColor Yellow
$test5 = @{
    senderUserId = "user005"
    groupId = "group001"
    content = "收到，谢谢"
    timestamp = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
} | ConvertTo-Json

$response5 = Invoke-RestMethod -Uri "$baseUrl/webhook" -Method POST -Body $test5 -ContentType "application/json; charset=utf-8"
Write-Host "响应：" -ForegroundColor Cyan
$response5 | ConvertTo-Json -Depth 10
Write-Host ""
Start-Sleep -Seconds 2

# 测试6：带图片的消息
Write-Host "测试6: 带图片URL的消息" -ForegroundColor Yellow
$test6 = @{
    senderUserId = "user006"
    groupId = "group001"
    content = "3-201的卫生间漏水了"
    imageUrl = "https://example.com/image.jpg"
    timestamp = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
} | ConvertTo-Json

$response6 = Invoke-RestMethod -Uri "$baseUrl/webhook" -Method POST -Body $test6 -ContentType "application/json; charset=utf-8"
Write-Host "响应：" -ForegroundColor Cyan
$response6 | ConvertTo-Json -Depth 10
Write-Host ""
Start-Sleep -Seconds 2

# 测试7：多轮对话测试（先发不完整信息，再补全）
Write-Host "测试7: 多轮对话测试（先发不完整信息）" -ForegroundColor Yellow
$test7a = @{
    senderUserId = "user007"
    groupId = "group001"
    content = "灯坏了"
    timestamp = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
} | ConvertTo-Json

$response7a = Invoke-RestMethod -Uri "$baseUrl/webhook" -Method POST -Body $test7a -ContentType "application/json; charset=utf-8"
Write-Host "第一轮响应：" -ForegroundColor Cyan
$response7a | ConvertTo-Json -Depth 10
Write-Host ""

Start-Sleep -Seconds 2

Write-Host "测试7: 多轮对话测试（补全位置信息）" -ForegroundColor Yellow
$test7b = @{
    senderUserId = "user007"
    groupId = "group001"
    content = "在3-201的客厅"
    timestamp = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
} | ConvertTo-Json

$response7b = Invoke-RestMethod -Uri "$baseUrl/webhook" -Method POST -Body $test7b -ContentType "application/json; charset=utf-8"
Write-Host "第二轮响应：" -ForegroundColor Cyan
$response7b | ConvertTo-Json -Depth 10
Write-Host ""

# 测试8：参数校验测试（缺少senderUserId）
Write-Host "测试8: 参数校验测试（缺少senderUserId）" -ForegroundColor Yellow
$test8 = @{
    groupId = "group001"
    content = "测试消息"
} | ConvertTo-Json

try {
    $response8 = Invoke-RestMethod -Uri "$baseUrl/webhook" -Method POST -Body $test8 -ContentType "application/json; charset=utf-8"
    Write-Host "响应：" -ForegroundColor Cyan
    $response8 | ConvertTo-Json -Depth 10
} catch {
    Write-Host "错误响应（预期）：" -ForegroundColor Red
    Write-Host $_.Exception.Message
}
Write-Host ""

# 测试9：查询草稿接口
Write-Host "测试9: 查询草稿接口" -ForegroundColor Yellow
try {
    $response9 = Invoke-RestMethod -Uri "$baseUrl/drafts?groupId=group001" -Method GET
    Write-Host "响应：" -ForegroundColor Cyan
    $response9 | ConvertTo-Json -Depth 10
} catch {
    Write-Host "错误：" -ForegroundColor Red
    Write-Host $_.Exception.Message
}
Write-Host ""

Write-Host "=========================================" -ForegroundColor Green
Write-Host "测试完成！" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green
