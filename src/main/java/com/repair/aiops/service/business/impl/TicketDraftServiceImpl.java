package com.repair.aiops.service.business.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.repair.aiops.mapper.TicketDraftMapper;
import com.repair.aiops.model.entity.TicketDraftEntity;
import com.repair.aiops.service.business.ITicketDraftService;
import org.springframework.stereotype.Service; // 必须有这个导入

@Service
public class TicketDraftServiceImpl
        extends ServiceImpl<TicketDraftMapper, TicketDraftEntity>
        implements ITicketDraftService {
}
