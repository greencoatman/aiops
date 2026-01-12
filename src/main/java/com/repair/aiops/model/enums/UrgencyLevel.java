package com.repair.aiops.model.enums;

import lombok.Getter;

/**
 * 紧急程度枚举
 */
@Getter
public enum UrgencyLevel {
    /**
     * 紧急：涉及安全、停水、停电等重大影响
     */
    HIGH,

    /**
     * 普通：日常报修，不影响基本生活
     */
    MEDIUM,

    /**
     * 低：建议性意见或非即时性诉求
     */
    LOW
}
