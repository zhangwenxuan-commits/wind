package com.kama.jchatmind.mapper;

import com.kama.jchatmind.model.entity.DiagnosisReport;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DiagnosisReportMapper {
    int insert(DiagnosisReport diagnosisReport);

    int updateById(DiagnosisReport diagnosisReport);

    DiagnosisReport selectById(String id);

    DiagnosisReport selectLatestByTaskId(String taskId);

    List<DiagnosisReport> selectAll();
}
