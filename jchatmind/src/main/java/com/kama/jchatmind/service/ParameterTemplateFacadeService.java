package com.kama.jchatmind.service;

import com.kama.jchatmind.model.request.CreateParameterTemplateRequest;
import com.kama.jchatmind.model.request.UpdateParameterTemplateRequest;
import com.kama.jchatmind.model.response.CreateParameterTemplateResponse;
import com.kama.jchatmind.model.response.GetParameterTemplateResponse;
import com.kama.jchatmind.model.response.GetParameterTemplatesResponse;

public interface ParameterTemplateFacadeService {
    GetParameterTemplatesResponse getParameterTemplates();

    GetParameterTemplateResponse getParameterTemplate(String templateId);

    CreateParameterTemplateResponse createParameterTemplate(CreateParameterTemplateRequest request);

    void updateParameterTemplate(String templateId, UpdateParameterTemplateRequest request);
}
