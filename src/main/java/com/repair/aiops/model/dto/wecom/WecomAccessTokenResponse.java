package com.repair.aiops.model.dto.wecom;

import lombok.Data;

@Data
public class WecomAccessTokenResponse {
    private Integer errcode;
    private String errmsg;
    private String access_token;
    private Integer expires_in;
}
