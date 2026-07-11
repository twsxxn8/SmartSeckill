package com.smartseckill.common.page;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import lombok.Data;

/**
 * 分页请求参数。
 * <p>
 * 作为 Controller 方法的查询参数接收，配合 {@code @Valid} 进行参数校验。
 * </p>
 */
@Data
@Schema(description = "分页请求参数")
public class PageRequest {

    @Schema(description = "当前页码", defaultValue = "1")
    @Min(value = 1, message = "页码最小为1")
    private long page = 1;

    @Schema(description = "每页条数", defaultValue = "10")
    @Min(value = 1, message = "每页条数最小为1")
    @Max(value = 100, message = "每页条数最大为100")
    private long size = 10;

    @Schema(description = "排序字段")
    private String orderBy;

    @Schema(description = "是否升序", defaultValue = "false")
    private boolean asc = false;
}
