package com.repair.aiops.model.dto.wecom;

import lombok.Data;

@Data
public class WecomChatFetchRequest {
    private Long seq;
    private Integer limit;
}
