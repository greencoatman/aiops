package com.repair.aiops.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("work_order_drafts")
public class TicketDraftEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String groupId;       // 群ID（侧边栏查询的关键）
    private String senderId;      // 发送人ID
    private String content;       // 原始消息
    private String aiAnalysis;    // AI 分析后的 JSON 结果
    private Integer status;       // 0-待确认, 1-已转工单, 2-已忽略
    private LocalDateTime createTime;
}
