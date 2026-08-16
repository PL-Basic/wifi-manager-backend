package com.plagod.support;

/**
 * 跨服务稳定使用的分页边界。
 */
public final class PageBounds {

    private static final int DEFAULT_CURRENT = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    private final int current;
    private final int size;

    private PageBounds(int current, int size) {
        this.current = current;
        this.size = size;
    }

    public static PageBounds of(Integer current, Integer size) {
        int normalizedCurrent =
                current == null || current < DEFAULT_CURRENT
                        ? DEFAULT_CURRENT
                        : current;
        int normalizedSize =
                size == null || size < 1
                        ? DEFAULT_SIZE
                        : Math.min(size, MAX_SIZE);
        return new PageBounds(normalizedCurrent, normalizedSize);
    }

    public int getCurrent() {
        return current;
    }

    public int getSize() {
        return size;
    }

    public long getOffset() {
        return ((long) current - 1L) * size;
    }
}
