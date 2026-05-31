package com.kama.jchatmind.service;

import com.kama.jchatmind.model.request.ConfirmDiagnosisTaskRequest;
import com.kama.jchatmind.model.request.CreateDiagnosisTaskRequest;
import com.kama.jchatmind.model.request.UpdateDiagnosisTaskRequest;
import com.kama.jchatmind.model.response.CreateDiagnosisTaskResponse;
import com.kama.jchatmind.model.response.GetDiagnosisTaskResponse;
import com.kama.jchatmind.model.response.GetDiagnosisTasksResponse;

public interface DiagnosisTaskFacadeService {
    GetDiagnosisTasksResponse getDiagnosisTasks();

    GetDiagnosisTaskResponse getDiagnosisTask(String taskId);

    CreateDiagnosisTaskResponse createDiagnosisTask(CreateDiagnosisTaskRequest request);

    void updateDiagnosisTask(String taskId, UpdateDiagnosisTaskRequest request);

    GetDiagnosisTaskResponse startDiagnosisTask(String taskId);

    void confirmDiagnosisTask(String taskId, ConfirmDiagnosisTaskRequest request);
}
