package cn.miniants.platform.data.query;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.constraints.Positive;

import java.io.Serializable;

/**
 * 列表分页入参（filter/order 字符串由业务侧交给 {@link EntityQuery}）。
 */
public class PageParam<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Positive
    private Long page;

    @Positive
    private Long pageSize;

    private String filter;

    private String order;

    private T data;

    public Long getPage() {
        return page;
    }

    public void setPage(Long page) {
        this.page = page;
    }

    public Long getPageSize() {
        return pageSize;
    }

    public void setPageSize(Long pageSize) {
        this.pageSize = pageSize;
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    public String getOrder() {
        return order;
    }

    public void setOrder(String order) {
        this.order = order;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public <E> Page<E> page() {
        return page(20L);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public <E> Page<E> page(long size) {
        if (null == this.pageSize || this.pageSize < 1L) {
            this.pageSize = size;
        }
        if (null == this.page) {
            this.page = 1L;
        }
        return new Page(this.page, this.pageSize);
    }
}
