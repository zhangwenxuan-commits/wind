package com.kama.jchatmind.controller;

import com.kama.jchatmind.model.common.ApiResponse;
import com.kama.jchatmind.model.request.ConfirmDiagnosisTaskRequest;
import com.kama.jchatmind.model.request.CreateDiagnosisTaskRequest;
import com.kama.jchatmind.model.request.UpdateDiagnosisTaskRequest;
import com.kama.jchatmind.model.response.CreateDiagnosisTaskResponse;
import com.kama.jchatmind.model.response.GetDiagnosisTaskResponse;
import com.kama.jchatmind.model.response.GetDiagnosisTasksResponse;
import com.kama.jchatmind.service.DiagnosisTaskFacadeService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@AllArgsConstructor
public class DiagnosisTaskController {

    private final DiagnosisTaskFacadeService diagnosisTaskFacadeService;

    @GetMapping("/diagnosis-tasks")
    public ApiResponse<GetDiagnosisTasksResponse> getDiagnosisTasks() {
        return ApiResponse.success(diagnosisTaskFacadeService.getDiagnosisTasks());
    }

    @GetMapping("/diagnosis-tasks/{taskId}")
    public ApiResponse<GetDiagnosisTaskResponse> getDiagnosisTask(@PathVariable String taskId) {
        return ApiResponse.success(diagnosisTaskFacadeService.getDiagnosisTask(taskId));
    }

    @PostMapping("/diagnosis-tasks")
    public ApiResponse<CreateDiagnosisTaskResponse> createDiagnosisTask(
            @RequestBody CreateDiagnosisTaskRequest request
    ) {
        return ApiResponse.success(diagnosisTaskFacadeService.createDiagnosisTask(request));
    }

    @PatchMapping("/diagnosis-tasks/{taskId}")
    public ApiResponse<Void> updateDiagnosisTask(
            @PathVariable String taskId,
            @RequestBody UpdateDiagnosisTaskRequest request
    ) {
        diagnosisTaskFacadeService.updateDiagnosisTask(taskId, request);
        return ApiResponse.success();
    }

    @PostMapping("/diagnosis-tasks/{taskId}/start")
    public ApiResponse<GetDiagnosisTaskResponse> startDiagnosisTask(@PathVariable String taskId) {
        return ApiResponse.success(diagnosisTaskFacadeService.startDiagnosisTask(taskId));
    }

    @PostMapping("/diagnosis-tasks/{taskId}/confirm")
    public ApiResponse<Void> confirmDiagnosisTask(
            @PathVariable String taskId,
            @RequestBody(required = false) ConfirmDiagnosisTaskRequest request
    ) {
        diagnosisTaskFacadeService.confirmDiagnosisTask(
                taskId,
                request != null ? request : new ConfirmDiagnosisTaskRequest()
        );
        return ApiResponse.success();
    }
}
