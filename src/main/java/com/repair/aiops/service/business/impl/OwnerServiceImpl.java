package com.repair.aiops.service.business.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.repair.aiops.mapper.OwnerMapper;
import com.repair.aiops.model.entity.Owner;
import com.repair.aiops.service.business.IOwnerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OwnerServiceImpl extends ServiceImpl<OwnerMapper, Owner> implements IOwnerService {

    @Override
    public String getOwnerInfo(String senderId) {
        if (senderId == null || senderId.trim().isEmpty()) {
            return "未知身份业主";
        }
        
        try {
            // 使用 MyBatis-Plus 的 LambdaQuery 提高代码安全性
            Owner owner = this.getOne(new LambdaQueryWrapper<Owner>()
                    .eq(Owner::getSenderId, senderId));

            if (owner != null && owner.getRoomNumber() != null && owner.getOwnerName() != null) {
                return String.format("%s室, %s", owner.getRoomNumber(), owner.getOwnerName());
            }
            return "未知身份业主";
        } catch (Exception e) {
            log.error("获取业主信息异常：senderId={}, error={}", senderId, e.getMessage(), e);
            return "未知身份业主";
        }
    }

    @Override
    public void bindOwner(String senderId, String roomNumber, String name) {
        if (senderId == null || senderId.trim().isEmpty()) {
            log.warn("绑定业主失败：senderId为空");
            throw new IllegalArgumentException("发送者ID不能为空");
        }
        
        try {
            Owner owner = this.getOne(new LambdaQueryWrapper<Owner>()
                    .eq(Owner::getSenderId, senderId));

            if (owner == null) {
                owner = new Owner();
            }

            owner.setSenderId(senderId);
            owner.setRoomNumber(roomNumber);
            owner.setOwnerName(name);

            this.saveOrUpdate(owner);
            log.info("绑定业主成功：senderId={}, roomNumber={}, name={}", senderId, roomNumber, name);
        } catch (Exception e) {
            log.error("绑定业主异常：senderId={}, error={}", senderId, e.getMessage(), e);
            throw new RuntimeException("绑定业主失败：" + e.getMessage(), e);
        }
    }
}
