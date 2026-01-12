# 快速测试脚本
$baseUrl = "http://localhost:8080/api/wechat"

Write-Host "=========================================" -ForegroundColor Green
Write-Host "快速API测试" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green
Write-Host ""

# 测试1：完整报修消息
Write-Host "测试1: 完整报修消息（3-201灯坏了）" -ForegroundColor Yellow
$timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$body1 = @{
    senderUserId = "user001"
    groupId = "group001"
    content = "我家3-201的灯坏了，需要维修"
    timestamp = $timestamp
} | ConvertTo-Json

try {
    $response1 = Invoke-RestMethod -Uri "$baseUrl/webhook" -Method POST -Body $body1 -ContentType "application/json; charset=utf-8"
    Write-Host "状态: $($response1.status)" -ForegroundColor Cyan
    Write-Host "响应数据:" -ForegroundColor Cyan
    $response1 | ConvertTo-Json -Depth 5
} catch {
    Write-Host "错误: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $responseBody = $reader.ReadToEnd()
        Write-Host "响应体: $responseBody" -ForegroundColor Red
    }
}
Write-Host ""
Start-Sleep -Seconds 3

# 测试2：信息不全（只有描述）
Write-Host "测试2: 信息不全（只有描述）" -ForegroundColor Yellow
$timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$body2 = @{
    senderUserId = "user002"
    groupId = "group001"
    content = "灯坏了"
    timestamp = $timestamp
} | ConvertTo-Json

try {
    $response2 = Invoke-RestMethod -Uri "$baseUrl/webhook" -Method POST -Body $body2 -ContentType "application/json; charset=utf-8"
    Write-Host "状态: $($response2.status)" -ForegroundColor Cyan
    Write-Host "响应数据:" -ForegroundColor Cyan
    $response2 | ConvertTo-Json -Depth 5
} catch {
    Write-Host "错误: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""
Start-Sleep -Seconds 3

# 测试3：参数校验（缺少senderUserId）
Write-Host "测试3: 参数校验测试（缺少senderUserId）" -ForegroundColor Yellow
$body3 = @{
    groupId = "group001"
    content = "测试消息"
} | ConvertTo-Json

try {
    $response3 = Invoke-RestMethod -Uri "$baseUrl/webhook" -Method POST -Body $body3 -ContentType "application/json; charset=utf-8"
    Write-Host "响应:" -ForegroundColor Cyan
    $response3 | ConvertTo-Json
} catch {
    Write-Host "预期错误（参数校验）:" -ForegroundColor Yellow
    Write-Host $_.Exception.Message -ForegroundColor Red
}
Write-Host ""

# 测试4：查询草稿
Write-Host "测试4: 查询草稿接口" -ForegroundColor Yellow
try {
    $response4 = Invoke-RestMethod -Uri "$baseUrl/drafts?groupId=group001" -Method GET
    Write-Host "草稿数量: $($response4.Count)" -ForegroundColor Cyan
    if ($response4.Count -gt 0) {
        Write-Host "草稿列表:" -ForegroundColor Cyan
        $response4 | ConvertTo-Json -Depth 3
    } else {
        Write-Host "暂无草稿" -ForegroundColor Gray
    }
} catch {
    Write-Host "错误: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

Write-Host "=========================================" -ForegroundColor Green
Write-Host "测试完成！" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green
