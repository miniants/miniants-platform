package cn.miniants.platform.admin.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

public class PageResult<T> {

    private List<T> records;
    private long total;
    private long current;
    private long size;

    public static <T> PageResult<T> of(IPage<?> page, List<T> records) {
        PageResult<T> result = new PageResult<>();
        result.records = records;
        result.total = page.getTotal();
        result.current = page.getCurrent();
        result.size = page.getSize();
        return result;
    }

    public static <T> PageResult<T> of(List<T> records, long total, long current, long size) {
        PageResult<T> result = new PageResult<>();
        result.records = records;
        result.total = total;
        result.current = current;
        result.size = size;
        return result;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getCurrent() {
        return current;
    }

    public void setCurrent(long current) {
        this.current = current;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }
}
