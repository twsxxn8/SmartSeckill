package com.smartseckill.common.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 API 响应封装类。
 * <p>
 * 所有 Controller 接口均返回此类型，前端通过 {@code code == 200} 判断请求是否成功。
 * </p>
 *
 * @param <T> 响应数据的类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "统一响应结果")
public class Result<T> {

    /** 业务状态码，200 表示成功，其他值参见 {@link ResultCode} */
    @Schema(description = "状态码")
    private int code;

    /** 提示信息，失败时返回具体错误描述 */
    @Schema(description = "提示信息")
    private String message;

    /** 响应数据，无数据时为 null */
    @Schema(description = "数据")
    private T data;

    /** 成功响应（无数据） */
    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    /** 成功响应（携带数据） */
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /** 成功响应（自定义提示信息 + 数据） */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    /** 失败响应（使用预定义错误码） */
    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /** 失败响应（自定义状态码和消息） */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    /** 失败响应（使用预定义错误码 + 自定义消息覆盖默认描述） */
    public static <T> Result<T> fail(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null);
    }
}
