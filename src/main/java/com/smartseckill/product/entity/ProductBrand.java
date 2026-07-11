package com.smartseckill.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartseckill.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_brand")
public class ProductBrand extends BaseEntity {

    private String brandName;
    private String logo;
    private String description;
    private Integer sortOrder;
    private Integer status;
}
