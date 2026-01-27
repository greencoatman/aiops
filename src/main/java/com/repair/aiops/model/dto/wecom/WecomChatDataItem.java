package com.repair.aiops.model.dto.wecom;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class WecomChatDataItem {
    private Long seq;
    private String msgid;
    private Integer publickey_ver;
    private String encrypt_random_key;

    @JsonProperty("encrypt_chat_msg")
    private String encryptChatMsg;

    @JsonProperty("decrypt_chat_msg")
    private String decryptChatMsg;
}
