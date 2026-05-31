package com.kama.jchatmind.mapper;

import com.kama.jchatmind.model.entity.ParameterTemplate;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ParameterTemplateMapper {
    int insert(ParameterTemplate parameterTemplate);

    ParameterTemplate selectById(String id);

    List<ParameterTemplate> selectAll();

    int updateById(ParameterTemplate parameterTemplate);
}
