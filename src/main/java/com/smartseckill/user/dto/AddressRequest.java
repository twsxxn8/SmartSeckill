package com.smartseckill.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "地址请求")
public class AddressRequest {

    @NotBlank(message = "收货人姓名不能为空")
    @Schema(description = "收货人姓名")
    private String receiverName;

    @NotBlank(message = "收货人电话不能为空")
    @Schema(description = "收货人电话")
    private String receiverPhone;

    @Schema(description = "省份")
    private String province;

    @Schema(description = "城市")
    private String city;

    @Schema(description = "区县")
    private String district;

    @NotBlank(message = "详细地址不能为空")
    @Schema(description = "详细地址")
    private String detailAddress;

    @Schema(description = "是否默认地址")
    private Integer isDefault;
}
