package com.repair.aiops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.repair.aiops.model.entity.TicketDraftEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TicketDraftMapper extends BaseMapper<TicketDraftEntity> {
    // 继承 BaseMapper 后，基本的 CRUD 就有了
}
