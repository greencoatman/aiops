package com.repair.aiops.service.business;


import com.baomidou.mybatisplus.extension.service.IService;
import com.repair.aiops.model.entity.Owner;

/**
 * 面向接口编程：定义业务规范
 */
public interface IOwnerService extends IService<Owner> {

    // 获取业主描述，供 AI 使用
    String getOwnerInfo(String senderId);

    // 绑定业主身份
    void bindOwner(String senderId, String roomNumber, String name);
}
