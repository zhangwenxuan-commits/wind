package com.kama.jchatmind.model.request;

import lombok.Data;

@Data
public class CreateDiagnosisTaskRequest {
    private String title;
    private String deviceName;
    private String vibrationDocumentId;
    private String speedDocumentId;
    private String parameterTemplateId;
    private String parameterKbId;
    private String symptomHint;
    private String referenceShaft;
    private String envelopeBandHint;
}
