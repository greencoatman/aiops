package com.repair.aiops.service.wecom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class WecomRobotService {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final StringRedisTemplate redisTemplate;

    @Value("${wecom.bot.enabled:false}")
    private boolean webhookEnabled;

    @Value("${wecom.bot.webhook-url:}")
    private String webhookUrl;

    @Value("${wecom.bot.app-name:AI助手}")
    private String appName;

    // --- App 模式配置 ---
    @Value("${wecom.app.enabled:false}")
    private boolean appEnabled;

    @Value("${wecom.corp-id:}")
    private String corpId;

    @Value("${wecom.app.agent-id:}")
    private String agentId;

    @Value("${wecom.app.secret:}")
    private String appSecret;

    @Value("${wecom.app.notify-user:}")
    private String notifyUser;

    // --- 客户群消息（外部群） ---
    @Value("${wecom.customer-group.enabled:false}")
    private boolean customerGroupEnabled;

    @Value("${wecom.customer-group.sender-userid:}")
    private String customerGroupSender;

    @Autowired
    public WecomRobotService(RestTemplate restTemplate, ObjectMapper objectMapper, Environment environment, StringRedisTemplate redisTemplate) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.redisTemplate = redisTemplate;
    }

    public void sendMissingInfoNotice(String traceId, String groupId, String senderId,
                                      String missingInfo, String suggestedReply) {
        // 尝试解析名称
        String groupName = resolveGroupName(groupId);
        String senderName = resolveUserName(senderId);

        // 构建消息内容
        StringBuilder content = new StringBuilder();
        content.append("【").append(appName).append("】报修信息不完整\n");
        
        if (groupName != null) {
            content.append("群名: ").append(groupName).append("\n");
        } else {
            content.append("群ID: ").append(groupId).append("\n");
        }
        
        if (senderName != null) {
            content.append("发送者: ").append(senderName).append("\n");
        } else {
            content.append("发送者ID: ").append(senderId).append("\n");
        }

        if (StringUtils.hasText(missingInfo)) {
            content.append("缺失信息: ").append(missingInfo).append("\n");
        }
        if (StringUtils.hasText(suggestedReply)) {
            content.append("建议回复: ").append(suggestedReply).append("\n");
        }
        if (StringUtils.hasText(traceId)) {
            content.append("traceId: ").append(traceId);
        }

        // 1. 尝试发送应用消息 (私聊)
        if (appEnabled && StringUtils.hasText(agentId) && StringUtils.hasText(appSecret)) {
            sendAppMessage(content.toString());
        }

        // 2. 尝试发送 Webhook 消息 (群聊，仅限内部群)
        if (webhookEnabled && StringUtils.hasText(webhookUrl)) {
            sendWebhookMessage(content.toString());
        }

        // 3. 客户群（外部群）消息：直接发到对应群
        if (customerGroupEnabled && StringUtils.hasText(groupId)) {
            // 优先使用AI建议回复，其次回退到通知文本，并带上“@姓名”提示
            String reply = StringUtils.hasText(suggestedReply) ? suggestedReply : content.toString();
            String mentionName = StringUtils.hasText(senderName) ? senderName : senderId;
            String customerReply = buildCustomerGroupMention(mentionName, reply);
            sendCustomerGroupMessage(traceId, groupId, customerReply);
        }
    }

    /**
     * 发送工单处理结果通知
     */
    public void sendOrderResultNotice(String traceId, String groupId, String senderId,
                                      boolean success, String message, Object orderData) {
        // 尝试解析名称（失败则回退到ID）
        String groupName = resolveGroupName(groupId);
        String senderName = resolveUserName(senderId);

        StringBuilder content = new StringBuilder();
        content.append("【").append(appName).append("】处理完成\n");
        content.append("状态: ").append(success ? "✅ 下单成功" : "❌ 下单失败").append("\n");
        if (groupName != null) {
            content.append("群名: ").append(groupName).append("\n");
        } else if (StringUtils.hasText(groupId)) {
            content.append("群ID: ").append(groupId).append("\n");
        }
        if (senderName != null) {
            content.append("发送者: ").append(senderName).append("\n");
        } else {
            content.append("发送者ID: ").append(senderId).append("\n");
        }
        content.append("详情: ").append(message);
        if (StringUtils.hasText(traceId)) {
            content.append("\nTraceId: ").append(traceId);
        }

        if (appEnabled) {
            sendAppMessage(content.toString());
        }
        if (webhookEnabled) {
            sendWebhookMessage(content.toString());
        }
        if (customerGroupEnabled && StringUtils.hasText(groupId)) {
            // 对客户群只发送简短结果，避免过多内部字段，并带上“@姓名”提示
            String brief = success ? "报修已提交成功，我们会尽快安排人员处理。" : "报修提交失败，请稍后重试或联系管理员。";
            String mentionName = StringUtils.hasText(senderName) ? senderName : senderId;
            String customerReply = buildCustomerGroupMention(mentionName, brief);
            sendCustomerGroupMessage(traceId, groupId, customerReply);
        }
    }

    private void sendWebhookMessage(String content) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("msgtype", "text");
        payload.put("text", Map.of("content", content));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        try {
            restTemplate.postForEntity(webhookUrl, entity, String.class);
            log.info("Webhook消息发送成功");
        } catch (RestClientException e) {
            log.warn("Webhook消息发送失败: {}", e.getMessage());
        }
    }

    private void sendAppMessage(String content) {
        if (!StringUtils.hasText(notifyUser)) {
            log.warn("未配置通知接收人(notify-user)，无法发送应用消息");
            return;
        }

        try {
            String token = getAccessToken();
            if (token == null) return;

            String url = "https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=" + token;

            Map<String, Object> payload = new HashMap<>();
            payload.put("touser", notifyUser);
            payload.put("msgtype", "text");
            payload.put("agentid", agentId);
            payload.put("text", Map.of("content", content));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            log.info("应用消息发送结果: {}", response.getBody());

        } catch (Exception e) {
            log.error("应用消息发送异常: {}", e.getMessage());
        }
    }

    private void sendCustomerGroupMessage(String traceId, String chatId, String content) {
        if (!StringUtils.hasText(customerGroupSender)) {
            log.warn("未配置客户群发送人(sender-userid)，无法发送客户群消息");
            return;
        }
        String url = null;
        try {
            String token = getAccessToken();
            if (token == null) return;

            String urlFallback = "https://qyapi.weixin.qq.com/cgi-bin/externalcontact/add_msg_template?access_token=" + token;

            Map<String, Object> payload = new HashMap<>();
            payload.put("chat_type", "group");
            payload.put("sender", customerGroupSender);
            payload.put("chat_id_list", List.of(chatId));
            payload.put("msgtype", "text");
            payload.put("text", Map.of("content", content));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            url = urlFallback;
            ResponseEntity<byte[]> response = restTemplate.postForEntity(url, entity, byte[].class);
            String responseBody = response.getBody() != null
                    ? new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8)
                    : "";
            log.info("客户群消息发送结果: traceId={}, status={}, body={}", traceId, response.getStatusCode(), responseBody);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            String responseBody = e.getResponseBodyAsString();
            log.warn("客户群消息发送失败: traceId={}, status={}, url={}, body={}",
                    traceId, e.getStatusCode(), url, responseBody);
        } catch (Exception e) {
            log.warn("客户群消息发送异常: traceId={}, url={}, error={}", traceId, url, e.getMessage());
        }
    }

    private String buildCustomerGroupMention(String mentionName, String content) {
        if (!StringUtils.hasText(mentionName)) {
            return content;
        }
        String prefix = "@" + mentionName + "：";
        if (!StringUtils.hasText(content)) {
            return prefix;
        }
        // 避免重复前缀
        if (content.startsWith(prefix) || content.startsWith("@" + mentionName + " ")) {
            return content;
        }
        return prefix + content;
    }

    private String getAccessToken() {
        String url = "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=" + corpId + "&corpsecret=" + appSecret;
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.has("access_token")) {
                return root.get("access_token").asText();
            } else {
                log.error("获取AccessToken失败: {}", response.getBody());
                return null;
            }
        } catch (Exception e) {
            log.error("获取AccessToken异常: {}", e.getMessage());
            return null;
        }
    }

    private String resolveGroupName(String chatId) {
        if (!StringUtils.hasText(chatId)) return null;
        log.info(">> 开始解析群名: id={}", chatId);

        // 1. 优先查 Redis 缓存 (升级缓存Key版本，强制重新拉取以获取成员名单)
        String cacheKey = "wecom:cache:v2:group:" + chatId;
        String cachedName = redisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.hasText(cachedName)) {
            // 自动清洗乱码缓存 (简单判断是否包含乱码常见字符)
            if (cachedName.contains("æ") || cachedName.contains("å") || cachedName.contains("è")) {
                log.warn("发现Redis缓存中有乱码，自动失效: {}", cachedName);
                redisTemplate.delete(cacheKey);
            } else {
                log.info("<< 命中Redis缓存: {}", cachedName);
                return cachedName;
            }
        }
        
        // 2. 查本地配置映射表 (兜底)
        String mappedName = environment.getProperty("wecom.group.mapping." + chatId);
        if (StringUtils.hasText(mappedName)) {
            redisTemplate.opsForValue().set(cacheKey, mappedName, 7, TimeUnit.DAYS);
            log.info("<< 命中本地配置: {}", mappedName);
            return mappedName;
        }

        try {
            String token = getAccessToken();
            if (token == null) {
                log.warn("<< 无法解析: AccessToken为空");
                return null;
            }

            // 3. 调用客户群详情接口 (API)
            String url = "https://qyapi.weixin.qq.com/cgi-bin/externalcontact/groupchat/get?access_token=" + token;
            Map<String, Object> payload = new HashMap<>();
            payload.put("chat_id", chatId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            log.info("调用企业微信API查询群详情: url={}", url);
            // 强制使用 byte[] 接收，避免 RestTemplate 默认字符集导致的乱码
            ResponseEntity<byte[]> response = restTemplate.postForEntity(url, entity, byte[].class);
            String responseBody = new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8);
            
            JsonNode root = objectMapper.readTree(responseBody);
            
            if (root.has("group_chat")) {
                JsonNode groupChat = root.get("group_chat");
                
                // 1. 获取群名
                if (groupChat.has("name")) {
                    String name = groupChat.get("name").asText();
                    redisTemplate.opsForValue().set(cacheKey, name, 7, TimeUnit.DAYS);
                    log.info("<< API解析成功: name={}", name);
                    
                    // 2. 【关键】顺便缓存群成员的名字！解决非好友无法查询名字的问题
                    if (groupChat.has("member_list")) {
                        JsonNode memberList = groupChat.get("member_list");
                        int cachedCount = 0;
                        log.info(">> 群成员列表大小: {}", memberList.size());
                        
                        for (JsonNode member : memberList) {
                            // 调试日志：打印每个成员的关键字段
                            // log.debug("成员数据: {}", member);

                            String uid = member.has("userid") ? member.get("userid").asText() : null;
                            String uname = member.has("name") ? member.get("name").asText() : null;
                            String groupNickname = member.has("group_nickname") ? member.get("group_nickname").asText() : null;
                            int memberType = member.has("type") ? member.get("type").asInt() : 0; // 1=内部成员, 2=外部成员

                            if (!StringUtils.hasText(uid)) {
                                continue;
                            }

                            // 优先使用 name
                            String resolvedName = StringUtils.hasText(uname) ? uname : null;
                            // 其次尝试群昵称
                            if (!StringUtils.hasText(resolvedName) && StringUtils.hasText(groupNickname)) {
                                resolvedName = groupNickname;
                            }
                            // 内部成员再尝试走通讯录查询
                            if (!StringUtils.hasText(resolvedName) && memberType == 1) {
                                resolvedName = fetchInternalUserName(token, uid);
                            }

                            if (StringUtils.hasText(resolvedName)) {
                                redisTemplate.opsForValue().set("wecom:cache:user:" + uid, resolvedName, 7, TimeUnit.DAYS);
                                cachedCount++;
                            } else {
                                log.info("忽略无名成员: id={}, data={}", uid, member.toString());
                            }
                        }
                        log.info(">> 已顺便缓存群成员名单: {} 人", cachedCount);
                    }
                    
                    return name;
                }
            }
            
            log.warn("<< API调用成功但未获取到群名. 完整响应: {}", responseBody);
            log.warn("【排查建议】请检查应用的'可见范围'是否包含了该群的群主或创建人！");
        } catch (Exception e) {
            log.warn("<< 解析群名异常: chatId={}, error={}", chatId, e.getMessage());
        }
        return null;
    }

    private String resolveUserName(String userId) {
        if (!StringUtils.hasText(userId)) return null;
        log.info(">> 开始解析用户名: id={}", userId);

        // 1. 优先查 Redis 缓存
        String cacheKey = "wecom:cache:user:" + userId;
        String cachedName = redisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.hasText(cachedName)) {
            log.info("<< 命中Redis缓存: {}", cachedName);
            return cachedName;
        }

        // 2. 查本地配置 (兜底)
        String mappedName = environment.getProperty("wecom.user.mapping." + userId);
        if (StringUtils.hasText(mappedName)) {
            redisTemplate.opsForValue().set(cacheKey, mappedName, 7, TimeUnit.DAYS);
            log.info("<< 命中本地配置: {}", mappedName);
            return mappedName;
        }

        try {
            String token = getAccessToken();
            if (token == null) {
                log.warn("<< 无法解析: AccessToken为空");
                return null;
            }

            // 3. 尝试作为外部联系人查询 (API)
            String url = "https://qyapi.weixin.qq.com/cgi-bin/externalcontact/get?access_token=" + token + "&external_userid=" + userId;
            try {
                log.info("调用企业微信API查询外部联系人: url={}", url);
                ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);
                String responseBody = new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8);
                
                JsonNode root = objectMapper.readTree(responseBody);
                if (root.has("external_contact") && root.get("external_contact").has("name")) {
                    String name = root.get("external_contact").get("name").asText();
                    redisTemplate.opsForValue().set(cacheKey, name, 7, TimeUnit.DAYS);
                    log.info("<< API解析成功(外部): name={}", name);
                    return name;
                } else {
                     log.warn("API查询外部联系人失败. 完整响应: {}", responseBody);
                }
            } catch (Exception e) {
                log.warn("查询外部联系人异常: userId={}, error={}", userId, e.getMessage());
            }

            // 4. 尝试作为内部成员查询 (API)
            url = "https://qyapi.weixin.qq.com/cgi-bin/user/get?access_token=" + token + "&userid=" + userId;
            try {
                log.info("调用企业微信API查询内部成员: url={}", url);
                ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);
                String responseBody = new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8);

                JsonNode root = objectMapper.readTree(responseBody);
                if (root.has("name")) {
                    String name = root.get("name").asText();
                    redisTemplate.opsForValue().set(cacheKey, name, 7, TimeUnit.DAYS);
                    log.info("<< API解析成功(内部): name={}", name);
                    return name;
                } else {
                    log.warn("API查询内部成员失败. 完整响应: {}", responseBody);
                }
            } catch (Exception e) {
                log.warn("查询内部成员异常: userId={}, error={}", userId, e.getMessage());
            }

        } catch (Exception e) {
            log.warn("<< 解析用户名异常: userId={}, error={}", userId, e.getMessage());
        }
        return null;
    }

    private String fetchInternalUserName(String token, String userId) {
        if (!StringUtils.hasText(token) || !StringUtils.hasText(userId)) {
            return null;
        }
        try {
            String url = "https://qyapi.weixin.qq.com/cgi-bin/user/get?access_token=" + token + "&userid=" + userId;
            ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);
            String responseBody = new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.has("name")) {
                return root.get("name").asText();
            }
            log.warn("内部成员查询无name字段: userId={}, response={}", userId, responseBody);
        } catch (Exception e) {
            log.warn("查询内部成员异常: userId={}, error={}", userId, e.getMessage());
        }
        return null;
    }
}
