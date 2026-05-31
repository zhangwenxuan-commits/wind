package com.kama.jchatmind.controller;

import com.kama.jchatmind.model.common.ApiResponse;
import com.kama.jchatmind.model.response.GetParameterSourcesResponse;
import com.kama.jchatmind.service.ParameterSourceFacadeService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@AllArgsConstructor
public class ParameterSourceController {

    private final ParameterSourceFacadeService parameterSourceFacadeService;

    @GetMapping("/parameter-sources")
    public ApiResponse<GetParameterSourcesResponse> getParameterSources() {
        return ApiResponse.success(parameterSourceFacadeService.getParameterSources());
    }
}
