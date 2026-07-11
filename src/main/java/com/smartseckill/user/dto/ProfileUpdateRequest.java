package com.smartseckill.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 个人信息更新请求 DTO。
 * <p>所有字段均为可选，允许部分更新。</p>
 */
@Data
@Schema(description = "个人信息更新请求")
public class ProfileUpdateRequest {

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "头像URL")
    private String avatar;
}
