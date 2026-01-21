package com.repair.aiops.model.dto.wecom;

import lombok.Data;

import java.util.List;

@Data
public class WecomChatDataResponse {
    private Integer errcode;
    private String errmsg;
    private Long next_seq;
    private List<WecomChatDataItem> chatdata;
}
