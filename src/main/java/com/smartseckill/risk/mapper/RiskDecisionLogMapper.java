package com.smartseckill.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartseckill.risk.entity.RiskDecisionLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 风控决策日志 Mapper。
 */
@Mapper
public interface RiskDecisionLogMapper extends BaseMapper<RiskDecisionLog> {
}
