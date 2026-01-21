package com.repair.aiops.service.wecom;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class WecomRobotService {
    private final RestTemplate restTemplate;

    @Value("${wecom.bot.enabled:false}")
    private boolean enabled;

    @Value("${wecom.bot.webhook-url:}")
    private String webhookUrl;

    @Value("${wecom.bot.app-name:AI助手}")
    private String appName;

    public WecomRobotService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void sendMissingInfoNotice(String traceId, String groupId, String senderId,
                                      String missingInfo, String suggestedReply) {
        if (!enabled) {
            log.debug("企业微信群机器人未启用，跳过通知");
            return;
        }
        if (!StringUtils.hasText(webhookUrl)) {
            log.warn("企业微信群机器人 webhook 未配置，无法发送通知");
            return;
        }

        StringBuilder content = new StringBuilder();
        content.append("【").append(appName).append("】报修信息不完整\n")
                .append("群ID: ").append(groupId).append("\n")
                .append("发送者: ").append(senderId).append("\n");
        if (StringUtils.hasText(missingInfo)) {
            content.append("缺失信息: ").append(missingInfo).append("\n");
        }
        if (StringUtils.hasText(suggestedReply)) {
            content.append("建议回复: ").append(suggestedReply).append("\n");
        }
        if (StringUtils.hasText(traceId)) {
            content.append("traceId: ").append(traceId);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("msgtype", "text");
        payload.put("text", Map.of("content", content.toString()));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        try {
            restTemplate.postForEntity(webhookUrl, entity, String.class);
            log.info("企业微信群机器人通知已发送: traceId={}, groupId={}, senderId={}",
                    traceId, groupId, senderId);
        } catch (RestClientException e) {
            log.warn("企业微信群机器人通知失败: {}", e.getMessage());
        }
    }
}
