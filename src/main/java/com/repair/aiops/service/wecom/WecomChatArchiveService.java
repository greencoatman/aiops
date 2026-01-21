package com.repair.aiops.service.wecom;

import com.repair.aiops.model.dto.wecom.WecomAccessTokenResponse;
import com.repair.aiops.model.dto.wecom.WecomChatDataRequest;
import com.repair.aiops.model.dto.wecom.WecomChatDataResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class WecomChatArchiveService {
    @Value("${wecom.api.url.token:https://qyapi.weixin.qq.com/cgi-bin/gettoken}")
    private String tokenUrl;

    @Value("${wecom.api.url.chatdata:https://qyapi.weixin.qq.com/cgi-bin/msgaudit/getchatdata}")
    private String chatDataUrl;

    @Value("${wecom.api.url.media:https://qyapi.weixin.qq.com/cgi-bin/media/get}")
    private String mediaUrl;

    private final RestTemplate restTemplate;
    private final WecomChatDecryptor decryptor;

    @Value("${wecom.chat.archive.enabled:false}")
    private boolean enabled;

    @Value("${wecom.corp-id:}")
    private String corpId;

    @Value("${wecom.corp-secret:}")
    private String corpSecret;

    public WecomChatArchiveService(RestTemplate restTemplate, WecomChatDecryptor decryptor) {
        this.restTemplate = restTemplate;
        this.decryptor = decryptor;
    }

    public WecomChatDataResponse fetchChatData(Long seq, Integer limit) {
        if (!enabled) {
            log.warn("企业微信会话存档未启用，跳过拉取");
            return null;
        }
        if (!StringUtils.hasText(corpId) || !StringUtils.hasText(corpSecret)) {
            log.warn("企业微信 corp-id 或 corp-secret 未配置，无法拉取会话记录");
            return null;
        }

        String accessToken = fetchAccessToken();
        if (!StringUtils.hasText(accessToken)) {
            return null;
        }

        WecomChatDataRequest request = new WecomChatDataRequest(seq != null ? seq : 0L,
                limit != null ? limit : 50);
        String url = chatDataUrl + "?access_token=" + accessToken;

        try {
            ResponseEntity<WecomChatDataResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(request),
                    WecomChatDataResponse.class
            );
            WecomChatDataResponse body = response.getBody();
            if (body == null) {
                log.warn("企业微信会话记录返回为空");
                return null;
            }
            if (body.getErrcode() != null && body.getErrcode() != 0) {
                log.warn("企业微信会话记录拉取失败: errcode={}, errmsg={}", body.getErrcode(), body.getErrmsg());
                return null;
            }
            if (body.getChatdata() != null) {
                body.getChatdata().forEach(item -> {
                    if (item.getDecryptChatMsg() == null || item.getDecryptChatMsg().trim().isEmpty()) {
                        String decrypted = decryptor.decrypt(item.getEncryptChatMsg());
                        item.setDecryptChatMsg(decrypted);
                    }
                });
            }
            return body;
        } catch (RestClientException e) {
            log.error("拉取企业微信会话记录异常: {}", e.getMessage(), e);
            return null;
        }
    }

    public ResponseEntity<byte[]> fetchMedia(String sdkFileId) {
        if (!enabled) {
            log.warn("企业微信会话存档未启用，跳过图片获取");
            return ResponseEntity.status(503).build();
        }
        if (!StringUtils.hasText(corpId) || !StringUtils.hasText(corpSecret)) {
            log.warn("企业微信 corp-id 或 corp-secret 未配置，无法获取图片");
            return ResponseEntity.status(503).build();
        }
        if (!StringUtils.hasText(sdkFileId)) {
            return ResponseEntity.badRequest().build();
        }

        String accessToken = fetchAccessToken();
        if (!StringUtils.hasText(accessToken)) {
            return ResponseEntity.status(503).build();
        }

        try {
            String encoded = java.net.URLEncoder.encode(sdkFileId, java.nio.charset.StandardCharsets.UTF_8);
            String url = mediaUrl + "?access_token=" + accessToken + "&sdkfileid=" + encoded;
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    byte[].class
            );
            return response;
        } catch (RestClientException e) {
            log.error("获取企业微信图片异常: {}", e.getMessage(), e);
            return ResponseEntity.status(502).build();
        }
    }

    private String fetchAccessToken() {
        try {
            String url = tokenUrl + "?corpid=" + corpId + "&corpsecret=" + corpSecret;
            WecomAccessTokenResponse response = restTemplate.getForObject(url, WecomAccessTokenResponse.class);
            if (response == null) {
                log.warn("获取企业微信 access_token 返回为空");
                return null;
            }
            if (response.getErrcode() != null && response.getErrcode() != 0) {
                log.warn("获取企业微信 access_token 失败: errcode={}, errmsg={}",
                        response.getErrcode(), response.getErrmsg());
                return null;
            }
            return response.getAccess_token();
        } catch (RestClientException e) {
            log.error("获取企业微信 access_token 异常: {}", e.getMessage(), e);
            return null;
        }
    }
}
