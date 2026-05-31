package com.kama.jchatmind.mapper;

import com.kama.jchatmind.model.entity.DiagnosisTask;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DiagnosisTaskMapper {
    int insert(DiagnosisTask diagnosisTask);

    DiagnosisTask selectById(String id);

    List<DiagnosisTask> selectAll();

    int updateById(DiagnosisTask diagnosisTask);
}
