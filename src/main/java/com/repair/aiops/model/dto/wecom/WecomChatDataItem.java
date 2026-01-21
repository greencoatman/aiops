package com.repair.aiops.model.dto.wecom;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class WecomChatDataItem {
    private Long seq;

    @JsonProperty("encrypt_chat_msg")
    private String encryptChatMsg;

    @JsonProperty("decrypt_chat_msg")
    private String decryptChatMsg;
}
