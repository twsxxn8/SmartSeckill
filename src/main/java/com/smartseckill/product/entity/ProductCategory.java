package com.smartseckill.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartseckill.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_category")
public class ProductCategory extends BaseEntity {

    private Long parentId;
    private String categoryName;
    private String icon;
    private Integer sortOrder;
    private Integer status; // 1=启用, 0=禁用
}
