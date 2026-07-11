package com.smartseckill.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartseckill.product.entity.ProductBrand;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductBrandMapper extends BaseMapper<ProductBrand> {

    /** 根据分类 ID 列表，查询在该分类下有 SPU 的品牌（去重） */
    @Select("<script>SELECT DISTINCT b.* FROM product_brand b " +
            "INNER JOIN product_spu s ON b.id = s.brand_id " +
            "WHERE s.category_id IN <foreach collection='categoryIds' item='cid' open='(' separator=',' close=')'>#{cid}</foreach> " +
            "AND s.deleted = 0 AND s.status = 1 " +
            "AND b.deleted = 0 AND b.status = 1 " +
            "ORDER BY b.sort_order</script>")
    List<ProductBrand> selectBrandsByCategoryIds(@Param("categoryIds") List<Long> categoryIds);
}
