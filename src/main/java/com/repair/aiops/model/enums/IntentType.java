package com.repair.aiops.model.enums;

import lombok.Getter;

/**
 * 意图类型枚举
 */
@Getter
public enum IntentType {
    /**
     * 报修诉求：如“灯坏了”、“漏水了”
     */
    REPAIR,

    /**
     * 投诉诉求：如“有人乱停车”、“噪音太响”
     */
    COMPLAINT,

    /**
     * 咨询诉求：如“物业费多少钱”、“居委会电话多少”
     */
    INQUIRY,

    /**
     * 降噪类型：闲聊、表情包、无意义的回复（如“收到”、“谢谢”）
     */
    NOISE
}
