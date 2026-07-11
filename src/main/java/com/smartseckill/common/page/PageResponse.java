package com.smartseckill.common.page;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 分页响应封装类。
 * <p>
 * 将 MyBatis-Plus 的 {@link Page} 对象转换为前端友好的分页结构，
 * 配合 {@link PageRequest} 使用。
 * </p>
 *
 * @param <T> 列表元素类型
 */
@Data
@Schema(description = "分页响应结果")
public class PageResponse<T> {

    @Schema(description = "当前页码")
    private long page;

    @Schema(description = "每页条数")
    private long size;

    @Schema(description = "总记录数")
    private long total;

    @Schema(description = "总页数")
    private long pages;

    @Schema(description = "数据列表")
    private List<T> records;

    /** 从 MyBatis-Plus 分页对象转换 */
    public static <T> PageResponse<T> of(Page<T> page) {
        PageResponse<T> response = new PageResponse<>();
        response.setPage(page.getCurrent());
        response.setSize(page.getSize());
        response.setTotal(page.getTotal());
        response.setPages(page.getPages());
        response.setRecords(page.getRecords());
        return response;
    }

    /** 手动构造分页响应（适用于非 MyBatis-Plus 场景） */
    public static <T> PageResponse<T> of(List<T> records, long total, long page, long size) {
        PageResponse<T> response = new PageResponse<>();
        response.setPage(page);
        response.setSize(size);
        response.setTotal(total);
        response.setPages((total + size - 1) / size);
        response.setRecords(records);
        return response;
    }
}
