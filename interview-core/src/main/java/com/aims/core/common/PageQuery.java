package com.aims.core.common;

/**
 * 分页查询基座（P1 占位，P2 业务 CRUD 使用）。
 *
 * @param page 页码，从 1 开始
 * @param size 每页大小，上限 200
 */
public record PageQuery(int page, int size) {

    public static final int MAX_SIZE = 200;

    public PageQuery {
        if (page < 1) {
            throw new IllegalArgumentException("page 必须从 1 开始");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size 必须在 1~" + MAX_SIZE + " 之间");
        }
    }

    public static PageQuery of(int page, int size) {
        return new PageQuery(page, size);
    }

    public long offset() {
        return (long) (page - 1) * size;
    }
}
