package cn.miniants.platform.admin.mapper;

import cn.miniants.platform.admin.entity.Person;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PersonMapper extends BaseMapper<Person> {
}
