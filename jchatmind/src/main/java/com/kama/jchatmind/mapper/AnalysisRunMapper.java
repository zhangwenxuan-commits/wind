package com.kama.jchatmind.mapper;

import com.kama.jchatmind.model.entity.AnalysisRun;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AnalysisRunMapper {
    int insert(AnalysisRun analysisRun);

    AnalysisRun selectById(String id);

    AnalysisRun selectLatestByTaskId(String taskId);

    Integer selectNextRunNo(String taskId);

    List<AnalysisRun> selectByTaskId(String taskId);
}
