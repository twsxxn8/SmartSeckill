package com.smartseckill.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartseckill.product.entity.ProductSku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface ProductSkuMapper extends BaseMapper<ProductSku> {

    @Update("UPDATE product_sku SET status = #{status}, update_time = GETDATE() WHERE id = #{skuId} AND deleted = 0")
    int updateStatus(@Param("skuId") Long skuId, @Param("status") Integer status);

    /** 批量查询每个 SPU 的最低 SKU 售价 */
    @Select("<script>SELECT spu_id, MIN(price) AS min_price FROM product_sku " +
            "WHERE spu_id IN <foreach collection='spuIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "AND deleted = 0 AND status = 1 GROUP BY spu_id</script>")
    List<Map<String, Object>> selectMinPriceBySpuIds(@Param("spuIds") List<Long> spuIds);
}
