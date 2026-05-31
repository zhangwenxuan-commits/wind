package com.kama.jchatmind.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.converter.AnalysisEvidenceConverter;
import com.kama.jchatmind.converter.AnalysisRunConverter;
import com.kama.jchatmind.converter.DiagnosisReportConverter;
import com.kama.jchatmind.converter.DiagnosisTaskConverter;
import com.kama.jchatmind.converter.DocumentConverter;
import com.kama.jchatmind.converter.KnowledgeBaseConverter;
import com.kama.jchatmind.converter.ParameterTemplateConverter;
import com.kama.jchatmind.exception.BizException;
import com.kama.jchatmind.mapper.AnalysisEvidenceMapper;
import com.kama.jchatmind.mapper.AnalysisRunMapper;
import com.kama.jchatmind.mapper.DiagnosisReportMapper;
import com.kama.jchatmind.mapper.DiagnosisTaskMapper;
import com.kama.jchatmind.mapper.DocumentMapper;
import com.kama.jchatmind.mapper.KnowledgeBaseMapper;
import com.kama.jchatmind.mapper.ParameterTemplateMapper;
import com.kama.jchatmind.model.dto.DiagnosisTaskDTO;
import com.kama.jchatmind.model.dto.DocumentDTO;
import com.kama.jchatmind.model.entity.AnalysisEvidence;
import com.kama.jchatmind.model.entity.AnalysisRun;
import com.kama.jchatmind.model.entity.DiagnosisReport;
import com.kama.jchatmind.model.entity.DiagnosisTask;
import com.kama.jchatmind.model.entity.Document;
import com.kama.jchatmind.model.request.ConfirmDiagnosisTaskRequest;
import com.kama.jchatmind.model.request.CreateDiagnosisTaskRequest;
import com.kama.jchatmind.model.response.GetDiagnosisTaskResponse;
import com.kama.jchatmind.service.diagnosis.DiagnosisReportBuilder;
import com.kama.jchatmind.service.diagnosis.DiagnosisRuleProfileResolver;
import com.kama.jchatmind.service.diagnosis.DiagnosisTaskAnalyzer;
import com.kama.jchatmind.service.diagnosis.DiagnosisThresholdProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiagnosisTaskFacadeServiceImplTest {

    private DiagnosisTaskMapper diagnosisTaskMapper;
    private DocumentMapper documentMapper;
    private KnowledgeBaseMapper knowledgeBaseMapper;
    private ParameterTemplateMapper parameterTemplateMapper;
    private AnalysisRunMapper analysisRunMapper;
    private AnalysisEvidenceMapper analysisEvidenceMapper;
    private DiagnosisReportMapper diagnosisReportMapper;
    private DiagnosisTaskAnalyzer diagnosisTaskAnalyzer;
    private DiagnosisRuleProfileResolver diagnosisRuleProfileResolver;
    private DiagnosisReportBuilder diagnosisReportBuilder;
    private DiagnosisTaskFacadeServiceImpl service;
    private DiagnosisTaskConverter diagnosisTaskConverter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        diagnosisTaskMapper = mock(DiagnosisTaskMapper.class);
        documentMapper = mock(DocumentMapper.class);
        knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        parameterTemplateMapper = mock(ParameterTemplateMapper.class);
        analysisRunMapper = mock(AnalysisRunMapper.class);
        analysisEvidenceMapper = mock(AnalysisEvidenceMapper.class);
        diagnosisReportMapper = mock(DiagnosisReportMapper.class);
        diagnosisTaskAnalyzer = mock(DiagnosisTaskAnalyzer.class);
        diagnosisRuleProfileResolver = mock(DiagnosisRuleProfileResolver.class);
        diagnosisReportBuilder = mock(DiagnosisReportBuilder.class);

        diagnosisTaskConverter = new DiagnosisTaskConverter(objectMapper);
        service = new DiagnosisTaskFacadeServiceImpl(
                diagnosisTaskMapper,
                diagnosisTaskConverter,
                documentMapper,
                new DocumentConverter(objectMapper),
                knowledgeBaseMapper,
                new KnowledgeBaseConverter(objectMapper),
                parameterTemplateMapper,
                new ParameterTemplateConverter(objectMapper),
                analysisRunMapper,
                new AnalysisRunConverter(objectMapper),
                analysisEvidenceMapper,
                new AnalysisEvidenceConverter(objectMapper),
                diagnosisReportMapper,
                new DiagnosisReportConverter(),
                diagnosisTaskAnalyzer,
                diagnosisRuleProfileResolver,
                diagnosisReportBuilder
        );
    }

    @Test
    void shouldCreateReadyTask() {
        when(documentMapper.selectById(eq("doc-1"))).thenReturn(Document.builder().id("doc-1").build());
        when(diagnosisTaskMapper.insert(any(DiagnosisTask.class))).thenReturn(1);

        CreateDiagnosisTaskRequest request = new CreateDiagnosisTaskRequest();
        request.setTitle("HSS 轴承诊断");
        request.setVibrationDocumentId("doc-1");

        service.createDiagnosisTask(request);

        verify(diagnosisTaskMapper).insert(any(DiagnosisTask.class));
    }

    @Test
    void shouldRejectCreateWithoutVibrationDocument() {
        CreateDiagnosisTaskRequest request = new CreateDiagnosisTaskRequest();
        request.setTitle("bad-task");

        BizException exception = assertThrows(BizException.class, () -> service.createDiagnosisTask(request));

        assertEquals("请选择振动信号文件", exception.getMessage());
    }

    @Test
    void shouldStartTaskAndPersistStructuredAnalysis() throws Exception {
        DiagnosisTask existing = diagnosisTaskConverter.toEntity(DiagnosisTaskDTO.builder()
                .id("task-1")
                .title("task")
                .status("READY")
                .riskLevel("UNKNOWN")
                .vibrationDocumentId("doc-1")
                .speedDocumentId("doc-2")
                .summary("任务已创建，等待执行分析。")
                .metadata(new DiagnosisTaskDTO.MetaData())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        DocumentDTO.BasicStats basicStats = new DocumentDTO.BasicStats();
        basicStats.setCrestFactor(4.8);
        DiagnosisTaskDTO.AnalysisSnapshot snapshot = new DiagnosisTaskDTO.AnalysisSnapshot();
        snapshot.setBasicStats(basicStats);
        snapshot.setConclusion("检测到疑似轴承故障。");
        snapshot.setRecommendation("建议安排复核。");
        snapshot.setEvidence(List.of("峰值因子异常"));
        snapshot.setAppliedRules(List.of(new DiagnosisTaskDTO.AppliedRuleSummary()));

        DiagnosisTaskDTO.MetaData updatedMetaData = new DiagnosisTaskDTO.MetaData();
        updatedMetaData.setLatestAnalysis(snapshot);
        DiagnosisTask updated = diagnosisTaskConverter.toEntity(DiagnosisTaskDTO.builder()
                .id("task-1")
                .title("task")
                .status("REVIEW")
                .riskLevel("MEDIUM")
                .vibrationDocumentId("doc-1")
                .speedDocumentId("doc-2")
                .summary("检测到疑似轴承故障。")
                .metadata(updatedMetaData)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        when(diagnosisTaskMapper.selectById(eq("task-1"))).thenReturn(existing, updated);
        when(documentMapper.selectById(eq("doc-1"))).thenReturn(Document.builder().id("doc-1").build());
        when(documentMapper.selectById(eq("doc-2"))).thenReturn(Document.builder().id("doc-2").build());
        when(diagnosisTaskMapper.updateById(any(DiagnosisTask.class))).thenReturn(1);
        when(diagnosisRuleProfileResolver.resolve(any())).thenReturn(DiagnosisThresholdProfile.defaults());
        when(diagnosisTaskAnalyzer.analyze(any(DiagnosisTaskDTO.class), any(DiagnosisThresholdProfile.class)))
                .thenReturn(DiagnosisTaskAnalyzer.AnalysisResult.builder()
                        .riskLevel("MEDIUM")
                        .summary("检测到疑似轴承故障。")
                        .snapshot(snapshot)
                        .build());
        when(analysisRunMapper.selectNextRunNo(eq("task-1"))).thenReturn(1);
        when(analysisRunMapper.insert(any(AnalysisRun.class))).thenAnswer(invocation -> {
            AnalysisRun run = invocation.getArgument(0);
            run.setId("run-1");
            return 1;
        });
        when(analysisEvidenceMapper.insert(any(AnalysisEvidence.class))).thenAnswer(invocation -> {
            AnalysisEvidence evidence = invocation.getArgument(0);
            evidence.setId("evidence-1");
            return 1;
        });
        when(diagnosisReportMapper.selectLatestByTaskId(eq("task-1")))
                .thenReturn(null, DiagnosisReport.builder()
                        .id("report-1")
                        .taskId("task-1")
                        .runId("run-1")
                        .version(1)
                        .status("DRAFT")
                        .title("task - 诊断报告")
                        .summary("检测到疑似轴承故障。")
                        .contentMarkdown("report")
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build());
        when(diagnosisReportBuilder.buildMarkdown(any(), any(), any())).thenReturn("report");
        when(diagnosisReportMapper.insert(any(DiagnosisReport.class))).thenAnswer(invocation -> {
            DiagnosisReport report = invocation.getArgument(0);
            report.setId("report-1");
            return 1;
        });

        GetDiagnosisTaskResponse response = service.startDiagnosisTask("task-1");

        verify(diagnosisTaskMapper).updateById(any(DiagnosisTask.class));
        assertEquals("REVIEW", response.getTask().getStatus());
        assertEquals("MEDIUM", response.getTask().getRiskLevel());
        assertEquals("检测到疑似轴承故障。", response.getTask().getLatestAnalysis().getConclusion());
        assertEquals("run-1", response.getTask().getLatestRun().getId());
        assertEquals("report-1", response.getTask().getLatestReport().getId());
    }

    @Test
    void shouldConfirmCompletedTask() throws Exception {
        DiagnosisTaskDTO.MetaData metaData = new DiagnosisTaskDTO.MetaData();
        metaData.setLatestAnalysis(new DiagnosisTaskDTO.AnalysisSnapshot());
        DiagnosisTask existing = diagnosisTaskConverter.toEntity(DiagnosisTaskDTO.builder()
                .id("task-1")
                .title("task")
                .status("REVIEW")
                .metadata(metaData)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
        when(diagnosisTaskMapper.selectById(eq("task-1"))).thenReturn(existing);
        when(diagnosisTaskMapper.updateById(any(DiagnosisTask.class))).thenReturn(1);
        when(diagnosisReportMapper.selectLatestByTaskId(eq("task-1")))
                .thenReturn(DiagnosisReport.builder()
                        .id("report-1")
                        .taskId("task-1")
                        .status("DRAFT")
                        .version(1)
                        .title("诊断报告")
                        .contentMarkdown("report")
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build());
        when(diagnosisReportMapper.updateById(any(DiagnosisReport.class))).thenReturn(1);

        ConfirmDiagnosisTaskRequest request = new ConfirmDiagnosisTaskRequest();
        request.setConfirmedBy("alice");
        service.confirmDiagnosisTask("task-1", request);

        verify(diagnosisTaskMapper).updateById(any(DiagnosisTask.class));
        verify(diagnosisReportMapper).updateById(any(DiagnosisReport.class));
    }
}
