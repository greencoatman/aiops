package com.repair.aiops.model.dto.wecom;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WecomChatDataRequest {
    private Long seq;
    private Integer limit;
}
