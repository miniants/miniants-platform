package cn.miniants.platform.admin.support;

public final class PageQueries {

    public static final long DEFAULT_SIZE = 20;
    public static final long MAX_SIZE = 200;

    private PageQueries() {
    }

    public static long current(Long current) {
        if (current == null || current < 1) {
            return 1;
        }
        return current;
    }

    public static long size(Long size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    /**
     * 列表筛选项常被前端传成空串。空串不是 0（0 是合法主键）。
     */
    public static Long optionalLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("参数格式不正确");
        }
    }
}
