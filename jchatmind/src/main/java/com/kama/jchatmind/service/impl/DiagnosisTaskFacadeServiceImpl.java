package com.kama.jchatmind.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kama.jchatmind.converter.*;
import com.kama.jchatmind.exception.BizException;
import com.kama.jchatmind.mapper.*;
import com.kama.jchatmind.model.dto.*;
import com.kama.jchatmind.model.entity.*;
import com.kama.jchatmind.model.request.ConfirmDiagnosisTaskRequest;
import com.kama.jchatmind.model.request.CreateDiagnosisTaskRequest;
import com.kama.jchatmind.model.request.UpdateDiagnosisTaskRequest;
import com.kama.jchatmind.model.response.CreateDiagnosisTaskResponse;
import com.kama.jchatmind.model.response.GetDiagnosisTaskResponse;
import com.kama.jchatmind.model.response.GetDiagnosisTasksResponse;
import com.kama.jchatmind.model.vo.*;
import com.kama.jchatmind.service.DiagnosisTaskFacadeService;
import com.kama.jchatmind.service.diagnosis.DiagnosisTaskAnalyzer;
import com.kama.jchatmind.service.diagnosis.DiagnosisReportBuilder;
import com.kama.jchatmind.service.diagnosis.DiagnosisRuleProfileResolver;
import com.kama.jchatmind.service.diagnosis.DiagnosisThresholdProfile;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class DiagnosisTaskFacadeServiceImpl implements DiagnosisTaskFacadeService {

    private final DiagnosisTaskMapper diagnosisTaskMapper;
    private final DiagnosisTaskConverter diagnosisTaskConverter;
    private final DocumentMapper documentMapper;
    private final DocumentConverter documentConverter;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeBaseConverter knowledgeBaseConverter;
    private final ParameterTemplateMapper parameterTemplateMapper;
    private final ParameterTemplateConverter parameterTemplateConverter;
    private final AnalysisRunMapper analysisRunMapper;
    private final AnalysisRunConverter analysisRunConverter;
    private final AnalysisEvidenceMapper analysisEvidenceMapper;
    private final AnalysisEvidenceConverter analysisEvidenceConverter;
    private final DiagnosisReportMapper diagnosisReportMapper;
    private final DiagnosisReportConverter diagnosisReportConverter;
    private final DiagnosisTaskAnalyzer diagnosisTaskAnalyzer;
    private final DiagnosisRuleProfileResolver diagnosisRuleProfileResolver;
    private final DiagnosisReportBuilder diagnosisReportBuilder;

    @Override
    public GetDiagnosisTasksResponse getDiagnosisTasks() {
        List<DiagnosisTaskVO> result = new ArrayList<>();
        for (DiagnosisTask task : diagnosisTaskMapper.selectAll()) {
            result.add(toVO(task));
        }
        return GetDiagnosisTasksResponse.builder()
                .tasks(result.toArray(new DiagnosisTaskVO[0]))
                .build();
    }

    @Override
    public GetDiagnosisTaskResponse getDiagnosisTask(String taskId) {
        return GetDiagnosisTaskResponse.builder()
                .task(toVO(requireTask(taskId)))
                .build();
    }

    @Override
    public CreateDiagnosisTaskResponse createDiagnosisTask(CreateDiagnosisTaskRequest request) {
        validateCreateRequest(request);
        try {
            DiagnosisTaskDTO dto = diagnosisTaskConverter.toDTO(request);
            normalizeReferences(dto);
            dto.setStatus("READY");
            dto.setRiskLevel("UNKNOWN");
            dto.setSummary("任务已创建，等待执行分析。");
            LocalDateTime now = LocalDateTime.now();
            dto.setCreatedAt(now);
            dto.setUpdatedAt(now);

            DiagnosisTask entity = diagnosisTaskConverter.toEntity(dto);
            int result = diagnosisTaskMapper.insert(entity);
            if (result <= 0) {
                throw new BizException("创建诊断任务失败");
            }
            return CreateDiagnosisTaskResponse.builder()
                    .taskId(entity.getId())
                    .build();
        } catch (JsonProcessingException e) {
            throw new BizException("创建诊断任务时发生序列化错误: " + e.getMessage());
        }
    }

    @Override
    public void updateDiagnosisTask(String taskId, UpdateDiagnosisTaskRequest request) {
        DiagnosisTask existing = requireTask(taskId);
        validateUpdateRequest(request);
        try {
            DiagnosisTaskDTO dto = diagnosisTaskConverter.toDTO(existing);
            diagnosisTaskConverter.updateDTOFromRequest(dto, request);
            normalizeReferences(dto);
            dto.setId(existing.getId());
            dto.setUpdatedAt(LocalDateTime.now());

            int result = diagnosisTaskMapper.updateById(diagnosisTaskConverter.toEntity(dto));
            if (result <= 0) {
                throw new BizException("更新诊断任务失败");
            }
        } catch (JsonProcessingException e) {
            throw new BizException("更新诊断任务时发生序列化错误: " + e.getMessage());
        }
    }

    @Override
    public GetDiagnosisTaskResponse startDiagnosisTask(String taskId) {
        DiagnosisTask existing = requireTask(taskId);
        try {
            DiagnosisTaskDTO task = diagnosisTaskConverter.toDTO(existing);
            ensureTaskReady(task);
            ParameterTemplateDTO parameterTemplate = loadParameterTemplateDTO(task.getParameterTemplateId());
            applyParameterTemplateDefaults(task, parameterTemplate);
            DiagnosisThresholdProfile thresholdProfile = diagnosisRuleProfileResolver.resolve(parameterTemplate);

            DiagnosisTaskAnalyzer.AnalysisResult analysisResult = diagnosisTaskAnalyzer.analyze(task, thresholdProfile);
            LocalDateTime now = LocalDateTime.now();

            AnalysisRunDTO run = persistAnalysisRun(task, analysisResult, now);
            List<AnalysisEvidenceDTO> evidence = persistAnalysisEvidence(run.getId(), analysisResult.getSnapshot(), now);
            DiagnosisReportDTO report = persistDiagnosisReport(task, run, evidence, now);

            DiagnosisTaskDTO.MetaData metadata = task.getMetadata() != null ? task.getMetadata() : new DiagnosisTaskDTO.MetaData();
            metadata.setLatestAnalysis(analysisResult.getSnapshot());
            task.setMetadata(metadata);
            task.setRiskLevel(analysisResult.getRiskLevel());
            task.setSummary(analysisResult.getSummary());
            task.setStatus("REVIEW");
            task.setUpdatedAt(now);

            int result = diagnosisTaskMapper.updateById(diagnosisTaskConverter.toEntity(task));
            if (result <= 0) {
                throw new BizException("执行诊断任务失败");
            }

            DiagnosisTaskVO taskVO = toVO(requireTask(taskId));
            taskVO.setLatestRun(analysisRunConverter.toVO(run));
            taskVO.setLatestReport(diagnosisReportConverter.toVO(report));
            return GetDiagnosisTaskResponse.builder().task(taskVO).build();
        } catch (JsonProcessingException e) {
            throw new BizException("执行诊断任务时发生序列化错误: " + e.getMessage());
        } catch (IOException e) {
            throw new BizException("执行诊断分析失败: " + e.getMessage());
        }
    }

    @Override
    public void confirmDiagnosisTask(String taskId, ConfirmDiagnosisTaskRequest request) {
        DiagnosisTask existing = requireTask(taskId);
        try {
            DiagnosisTaskDTO dto = diagnosisTaskConverter.toDTO(existing);
            DiagnosisTaskDTO.MetaData metadata = dto.getMetadata() != null ? dto.getMetadata() : new DiagnosisTaskDTO.MetaData();
            if (metadata.getLatestAnalysis() == null) {
                throw new BizException("任务尚未生成分析结果，无法确认");
            }
            metadata.setConfirmed(Boolean.TRUE);
            metadata.setConfirmedBy(StringUtils.hasText(request.getConfirmedBy()) ? request.getConfirmedBy() : "system");
            metadata.setConfirmedAt(LocalDateTime.now());
            dto.setMetadata(metadata);
            dto.setStatus("COMPLETED");
            dto.setUpdatedAt(LocalDateTime.now());

            int result = diagnosisTaskMapper.updateById(diagnosisTaskConverter.toEntity(dto));
            if (result <= 0) {
                throw new BizException("确认诊断任务失败");
            }

            DiagnosisReport latestReport = diagnosisReportMapper.selectLatestByTaskId(taskId);
            if (latestReport != null) {
                latestReport.setStatus("FINAL");
                latestReport.setUpdatedAt(LocalDateTime.now());
                diagnosisReportMapper.updateById(latestReport);
            }
        } catch (JsonProcessingException e) {
            throw new BizException("确认诊断任务时发生序列化错误: " + e.getMessage());
        }
    }

    private void ensureTaskReady(DiagnosisTaskDTO task) {
        if (!StringUtils.hasText(task.getVibrationDocumentId())) {
            throw new BizException("诊断任务缺少振动信号文件");
        }
        requireDocument(task.getVibrationDocumentId(), "振动信号文件不存在");
        if (StringUtils.hasText(task.getSpeedDocumentId())) {
            requireDocument(task.getSpeedDocumentId(), "转速信号文件不存在");
        }
    }

    private AnalysisRunDTO persistAnalysisRun(
            DiagnosisTaskDTO task,
            DiagnosisTaskAnalyzer.AnalysisResult analysisResult,
            LocalDateTime now
    ) throws JsonProcessingException {
        AnalysisRunDTO.MetaData runMetadata = new AnalysisRunDTO.MetaData();
        runMetadata.setBasicStats(analysisResult.getSnapshot().getBasicStats());
        runMetadata.setEvidence(analysisResult.getSnapshot().getEvidence());
        runMetadata.setAppliedRules(analysisResult.getSnapshot().getAppliedRules());
        runMetadata.setRecommendation(analysisResult.getSnapshot().getRecommendation());
        runMetadata.setConclusion(analysisResult.getSnapshot().getConclusion());

        Integer runNo = analysisRunMapper.selectNextRunNo(task.getId());
        AnalysisRunDTO run = AnalysisRunDTO.builder()
                .taskId(task.getId())
                .runNo(runNo != null ? runNo : 1)
                .status("COMPLETED")
                .riskLevel(analysisResult.getRiskLevel())
                .summary(analysisResult.getSummary())
                .metadata(runMetadata)
                .startedAt(analysisResult.getSnapshot().getStartedAt())
                .finishedAt(analysisResult.getSnapshot().getFinishedAt())
                .createdAt(now)
                .updatedAt(now)
                .build();

        AnalysisRun entity = analysisRunConverter.toEntity(run);
        int result = analysisRunMapper.insert(entity);
        if (result <= 0) {
            throw new BizException("保存分析运行记录失败");
        }
        run.setId(entity.getId());
        return run;
    }

    private List<AnalysisEvidenceDTO> persistAnalysisEvidence(
            String runId,
            DiagnosisTaskDTO.AnalysisSnapshot snapshot,
            LocalDateTime now
    ) throws JsonProcessingException {
        List<AnalysisEvidenceDTO> saved = new ArrayList<>();

        if (snapshot.getBasicStats() != null) {
            saved.add(insertEvidence(buildMetricEvidence(runId, snapshot.getBasicStats(), now)));
        }
        if (snapshot.getDominantPeaks() != null) {
            int index = 1;
            for (DiagnosisTaskDTO.PeakSummary peak : snapshot.getDominantPeaks()) {
                saved.add(insertEvidence(buildPeakEvidence(runId, peak, index++, now)));
            }
        }
        if (snapshot.getEvidence() != null) {
            int index = 1;
            for (String item : snapshot.getEvidence()) {
                saved.add(insertEvidence(buildTextEvidence(runId, item, index++, now)));
            }
        }
        return saved;
    }

    private AnalysisEvidenceDTO insertEvidence(AnalysisEvidenceDTO dto) throws JsonProcessingException {
        AnalysisEvidence entity = analysisEvidenceConverter.toEntity(dto);
        int result = analysisEvidenceMapper.insert(entity);
        if (result <= 0) {
            throw new BizException("保存分析证据失败");
        }
        dto.setId(entity.getId());
        return dto;
    }

    private AnalysisEvidenceDTO buildMetricEvidence(
            String runId,
            DocumentDTO.BasicStats basicStats,
            LocalDateTime now
    ) {
        return AnalysisEvidenceDTO.builder()
                .runId(runId)
                .evidenceType("STATISTIC_METRIC")
                .title("统计指标摘要")
                .content(String.format(
                        "峰值因子 %.2f，峭度 %.2f",
                        defaultDouble(basicStats.getCrestFactor()),
                        defaultDouble(basicStats.getKurtosis())
                ))
                .score(defaultDouble(basicStats.getCrestFactor()))
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private AnalysisEvidenceDTO buildPeakEvidence(
            String runId,
            DiagnosisTaskDTO.PeakSummary peak,
            int index,
            LocalDateTime now
    ) {
        AnalysisEvidenceDTO.MetaData metadata = new AnalysisEvidenceDTO.MetaData();
        metadata.setFrequencyHz(peak.getFrequencyHz());
        metadata.setAmplitude(peak.getAmplitude());
        metadata.setSource("latest_analysis");
        return AnalysisEvidenceDTO.builder()
                .runId(runId)
                .evidenceType("SPECTRUM_PEAK")
                .title("主导峰值 " + index)
                .content(String.format("频率 %.2f Hz，幅值 %.4f", peak.getFrequencyHz(), peak.getAmplitude()))
                .score(peak.getAmplitude())
                .metadata(metadata)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private AnalysisEvidenceDTO buildTextEvidence(
            String runId,
            String text,
            int index,
            LocalDateTime now
    ) {
        return AnalysisEvidenceDTO.builder()
                .runId(runId)
                .evidenceType("DIAGNOSIS_EVIDENCE")
                .title("诊断证据 " + index)
                .content(text)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private DiagnosisReportDTO persistDiagnosisReport(
            DiagnosisTaskDTO task,
            AnalysisRunDTO run,
            List<AnalysisEvidenceDTO> evidence,
            LocalDateTime now
    ) {
        DiagnosisReport existing = diagnosisReportMapper.selectLatestByTaskId(task.getId());
        int nextVersion = existing != null ? existing.getVersion() + 1 : 1;

        DiagnosisReportDTO report = DiagnosisReportDTO.builder()
                .taskId(task.getId())
                .runId(run.getId())
                .version(nextVersion)
                .status("DRAFT")
                .title(task.getTitle() + " - 诊断报告")
                .summary(run.getSummary())
                .contentMarkdown(diagnosisReportBuilder.buildMarkdown(task, run, evidence))
                .createdAt(now)
                .updatedAt(now)
                .build();
        DiagnosisReport entity = diagnosisReportConverter.toEntity(report);
        int result = diagnosisReportMapper.insert(entity);
        if (result <= 0) {
            throw new BizException("生成诊断报告失败");
        }
        report.setId(entity.getId());
        return report;
    }

    private DiagnosisTask requireTask(String taskId) {
        DiagnosisTask task = diagnosisTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException("诊断任务不存在: " + taskId);
        }
        return task;
    }

    private Document requireDocument(String documentId, String message) {
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BizException(message);
        }
        return document;
    }

    private DiagnosisTaskVO toVO(DiagnosisTask entity) {
        try {
            DiagnosisTaskDTO dto = diagnosisTaskConverter.toDTO(entity);
            return diagnosisTaskConverter.toVO(
                    dto,
                    loadDocumentVO(dto.getVibrationDocumentId()),
                    loadDocumentVO(dto.getSpeedDocumentId()),
                    loadParameterTemplateVO(dto.getParameterTemplateId()),
                    loadKnowledgeBaseVO(dto.getParameterKbId()),
                    loadLatestRunVO(dto.getId()),
                    loadLatestReportVO(dto.getId())
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private void applyParameterTemplateDefaults(DiagnosisTaskDTO task, ParameterTemplateDTO template) {
        if (template == null) {
            return;
        }
        DiagnosisTaskDTO.MetaData metadata = task.getMetadata() != null ? task.getMetadata() : new DiagnosisTaskDTO.MetaData();
        if (!StringUtils.hasText(metadata.getReferenceShaft()) && StringUtils.hasText(template.getReferenceShaft())) {
            metadata.setReferenceShaft(template.getReferenceShaft());
        }
        if (!StringUtils.hasText(metadata.getEnvelopeBandHint()) && StringUtils.hasText(template.getEnvelopeBandHint())) {
            metadata.setEnvelopeBandHint(template.getEnvelopeBandHint());
        }
        task.setMetadata(metadata);
    }

    private ParameterTemplateDTO loadParameterTemplateDTO(String templateId) throws JsonProcessingException {
        if (!StringUtils.hasText(templateId)) {
            return null;
        }
        ParameterTemplate template = parameterTemplateMapper.selectById(templateId);
        return template != null ? parameterTemplateConverter.toDTO(template) : null;
    }

    private DocumentVO loadDocumentVO(String documentId) throws JsonProcessingException {
        if (!StringUtils.hasText(documentId)) {
            return null;
        }
        Document document = documentMapper.selectById(documentId);
        return document != null ? documentConverter.toVO(document) : null;
    }

    private ParameterTemplateVO loadParameterTemplateVO(String templateId) throws JsonProcessingException {
        if (!StringUtils.hasText(templateId)) {
            return null;
        }
        ParameterTemplate template = parameterTemplateMapper.selectById(templateId);
        return template != null ? parameterTemplateConverter.toVO(parameterTemplateConverter.toDTO(template)) : null;
    }

    private KnowledgeBaseVO loadKnowledgeBaseVO(String knowledgeBaseId) throws JsonProcessingException {
        if (!StringUtils.hasText(knowledgeBaseId)) {
            return null;
        }
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(knowledgeBaseId);
        return knowledgeBase != null ? knowledgeBaseConverter.toVO(knowledgeBase) : null;
    }

    private AnalysisRunVO loadLatestRunVO(String taskId) throws JsonProcessingException {
        AnalysisRun latest = analysisRunMapper.selectLatestByTaskId(taskId);
        return latest != null ? analysisRunConverter.toVO(analysisRunConverter.toDTO(latest)) : null;
    }

    private DiagnosisReportVO loadLatestReportVO(String taskId) {
        DiagnosisReport report = diagnosisReportMapper.selectLatestByTaskId(taskId);
        return report != null ? diagnosisReportConverter.toVO(diagnosisReportConverter.toDTO(report)) : null;
    }

    private void validateCreateRequest(CreateDiagnosisTaskRequest request) {
        if (!StringUtils.hasText(request.getTitle())) {
            throw new BizException("任务名称不能为空");
        }
        if (!StringUtils.hasText(request.getVibrationDocumentId())) {
            throw new BizException("请选择振动信号文件");
        }
        if (documentMapper.selectById(request.getVibrationDocumentId()) == null) {
            throw new BizException("选择的振动信号文件不存在");
        }
        if (StringUtils.hasText(request.getSpeedDocumentId())
                && documentMapper.selectById(request.getSpeedDocumentId()) == null) {
            throw new BizException("选择的转速信号文件不存在");
        }
        if (StringUtils.hasText(request.getParameterTemplateId())
                && parameterTemplateMapper.selectById(request.getParameterTemplateId()) == null) {
            throw new BizException("选择的参数模板不存在");
        }
        if (StringUtils.hasText(request.getParameterKbId())
                && knowledgeBaseMapper.selectById(request.getParameterKbId()) == null) {
            throw new BizException("选择的参数源不存在");
        }
    }

    private void normalizeReferences(DiagnosisTaskDTO dto) {
        dto.setVibrationDocumentId(normalizeNullableId(dto.getVibrationDocumentId()));
        dto.setSpeedDocumentId(normalizeNullableId(dto.getSpeedDocumentId()));
        dto.setParameterTemplateId(normalizeNullableId(dto.getParameterTemplateId()));
        dto.setParameterKbId(normalizeNullableId(dto.getParameterKbId()));
    }

    private String normalizeNullableId(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private void validateUpdateRequest(UpdateDiagnosisTaskRequest request) {
        if (StringUtils.hasText(request.getVibrationDocumentId())
                && documentMapper.selectById(request.getVibrationDocumentId()) == null) {
            throw new BizException("选择的振动信号文件不存在");
        }
        if (StringUtils.hasText(request.getSpeedDocumentId())
                && documentMapper.selectById(request.getSpeedDocumentId()) == null) {
            throw new BizException("选择的转速信号文件不存在");
        }
        if (StringUtils.hasText(request.getParameterTemplateId())
                && parameterTemplateMapper.selectById(request.getParameterTemplateId()) == null) {
            throw new BizException("选择的参数模板不存在");
        }
        if (StringUtils.hasText(request.getParameterKbId())
                && knowledgeBaseMapper.selectById(request.getParameterKbId()) == null) {
            throw new BizException("选择的参数源不存在");
        }
    }

    private double defaultDouble(Double value) {
        return value != null ? value : 0.0;
    }
}
