package com.repair.aiops.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("owners") // 对应数据库表名
public class Owner {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String senderId;   // 企微发送者唯一ID
    private String roomNumber; // 房号
    private String ownerName;  // 业主姓名
    private String wechatName; // 微信昵称
    private String phoneNumber;
}
