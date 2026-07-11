package com.smartseckill.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartseckill.seckill.entity.SeckillOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 秒杀订单 Mapper。
 */
@Mapper
public interface SeckillOrderMapper extends BaseMapper<SeckillOrder> {
}
