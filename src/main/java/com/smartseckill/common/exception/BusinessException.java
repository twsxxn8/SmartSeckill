package com.smartseckill.common.exception;

import com.smartseckill.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常。
 * <p>
 * 在 Service 层抛出，由 {@link GlobalExceptionHandler} 捕获并转换为 {@link com.smartseckill.common.result.Result} 返回给前端。
 * </p>
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务错误码，对应 {@link com.smartseckill.common.result.ResultCode} 中的 code */
    private final int code;

    /** 使用预定义错误码构造（消息取枚举默认值） */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    /** 使用自定义错误码和消息构造 */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /** 使用预定义错误码 + 自定义消息构造（覆盖枚举默认描述） */
    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }
}
