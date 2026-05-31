package com.kama.jchatmind.model.request;

import com.kama.jchatmind.model.dto.ParameterTemplateDTO;
import lombok.Data;

@Data
public class UpdateParameterTemplateRequest {
    private String name;
    private String deviceModel;
    private String status;
    private String referenceShaft;
    private String envelopeBandHint;
    private ParameterTemplateDTO.Content content;
}
