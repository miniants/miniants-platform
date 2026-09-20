package cn.miniants.platform.data.service;

import cn.miniants.platform.data.mapper.CrudMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

public class BaseServiceImpl<M extends CrudMapper<T>, T> extends ServiceImpl<M, T>
        implements BaseService<T> {
}
