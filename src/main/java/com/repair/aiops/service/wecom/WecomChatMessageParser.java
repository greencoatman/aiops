package com.repair.aiops.service.wecom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repair.aiops.model.dto.GroupMsgDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WecomChatMessageParser {
    private final ObjectMapper objectMapper;
    private final String mediaBaseUrl;

    public WecomChatMessageParser(ObjectMapper objectMapper,
                                  @Value("${wecom.chat.archive.media-base-url:}") String mediaBaseUrl) {
        this.objectMapper = objectMapper;
        this.mediaBaseUrl = mediaBaseUrl != null ? mediaBaseUrl.trim() : "";
    }

    public GroupMsgDTO parse(String decryptedChatMsg) {
        if (decryptedChatMsg == null || decryptedChatMsg.trim().isEmpty()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(decryptedChatMsg);
            String msgType = getText(root, "msgtype");
            String sender = getText(root, "from");
            String roomId = getText(root, "roomid");
            Long msgTime = root.has("msgtime") ? root.get("msgtime").asLong() * 1000 : null;

            if (sender == null || roomId == null) {
                return null;
            }

            GroupMsgDTO dto = new GroupMsgDTO();
            dto.setSenderUserId(sender);
            dto.setGroupId(roomId);
            dto.setTimestamp(msgTime);

            if ("text".equals(msgType) && root.has("text")) {
                dto.setContent(getText(root.get("text"), "content"));
                return dto;
            }

            if ("image".equals(msgType) && root.has("image")) {
                // 企业微信会话存档的图片通常是 sdkfileid，后续需再换取图片URL
                String sdkFileId = getText(root.get("image"), "sdkfileid");
                if (sdkFileId != null && !sdkFileId.isEmpty() && !mediaBaseUrl.isEmpty()) {
                    dto.setImageUrl(mediaBaseUrl + "/" + sdkFileId);
                } else {
                    dto.setImageUrl(sdkFileId);
                }
                dto.setContent("");
                return dto;
            }

            // 其他类型暂不处理
            return null;
        } catch (Exception e) {
            log.warn("解析企业微信会话记录失败: {}", e.getMessage());
            return null;
        }
    }

    private String getText(JsonNode node, String field) {
        if (node == null || !node.has(field)) {
            return null;
        }
        String value = node.get(field).asText();
        return value != null && !value.trim().isEmpty() ? value : null;
    }
}
