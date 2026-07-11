package com.smartseckill.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartseckill.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户收货地址实体，对应表 {@code user_address}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_address")
public class UserAddress extends BaseEntity {

    /** 所属用户 ID */
    private Long userId;
    private String receiverName;
    private String receiverPhone;
    private String province;
    private String city;
    private String district;
    private String detailAddress;
    /** 是否默认地址：0=否，1=是 */
    private Integer isDefault;
}
