package com.smartseckill.seckill.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 秒杀请求 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "秒杀下单请求")
public class SeckillRequest {

    @NotNull(message = "活动ID不能为空")
    @Schema(description = "秒杀活动ID")
    private Long activityId;

    @NotNull(message = "防重Token不能为空")
    @Schema(description = "防重Token（通过 /seckill/token 接口获取）")
    private String token;
}
