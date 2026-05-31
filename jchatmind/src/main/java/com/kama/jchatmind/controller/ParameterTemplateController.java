package com.kama.jchatmind.controller;

import com.kama.jchatmind.model.common.ApiResponse;
import com.kama.jchatmind.model.request.CreateParameterTemplateRequest;
import com.kama.jchatmind.model.request.UpdateParameterTemplateRequest;
import com.kama.jchatmind.model.response.CreateParameterTemplateResponse;
import com.kama.jchatmind.model.response.GetParameterTemplateResponse;
import com.kama.jchatmind.model.response.GetParameterTemplatesResponse;
import com.kama.jchatmind.service.ParameterTemplateFacadeService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@AllArgsConstructor
public class ParameterTemplateController {

    private final ParameterTemplateFacadeService parameterTemplateFacadeService;

    @GetMapping("/parameter-templates")
    public ApiResponse<GetParameterTemplatesResponse> getParameterTemplates() {
        return ApiResponse.success(parameterTemplateFacadeService.getParameterTemplates());
    }

    @GetMapping("/parameter-templates/{templateId}")
    public ApiResponse<GetParameterTemplateResponse> getParameterTemplate(@PathVariable String templateId) {
        return ApiResponse.success(parameterTemplateFacadeService.getParameterTemplate(templateId));
    }

    @PostMapping("/parameter-templates")
    public ApiResponse<CreateParameterTemplateResponse> createParameterTemplate(
            @RequestBody CreateParameterTemplateRequest request
    ) {
        return ApiResponse.success(parameterTemplateFacadeService.createParameterTemplate(request));
    }

    @PatchMapping("/parameter-templates/{templateId}")
    public ApiResponse<Void> updateParameterTemplate(
            @PathVariable String templateId,
            @RequestBody UpdateParameterTemplateRequest request
    ) {
        parameterTemplateFacadeService.updateParameterTemplate(templateId, request);
        return ApiResponse.success();
    }
}
