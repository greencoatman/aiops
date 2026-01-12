package com.repair.aiops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.repair.aiops.model.entity.Owner;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OwnerMapper extends BaseMapper<Owner> {
    // 基础的 CRUD 已经由 BaseMapper 自动提供
}
