package com.kama.jchatmind.model.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DiagnosisReport {
    private String id;
    private String taskId;
    private String runId;
    private Integer version;
    private String status;
    private String title;
    private String summary;
    private String contentMarkdown;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
