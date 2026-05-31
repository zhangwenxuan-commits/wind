import { useCallback, useEffect, useState } from "react";
import {
  confirmDiagnosisTask,
  createDiagnosisTask,
  getDiagnosisTask,
  getDiagnosisTasks,
  startDiagnosisTask,
  type CreateDiagnosisTaskRequest,
  type DiagnosisTaskVO,
} from "../api/api.ts";

export function useDiagnosisTasks(taskId?: string) {
  const [tasks, setTasks] = useState<DiagnosisTaskVO[]>([]);
  const [task, setTask] = useState<DiagnosisTaskVO | null>(null);
  const [loading, setLoading] = useState(false);

  const fetchTasks = useCallback(async () => {
    setLoading(true);
    try {
      const response = await getDiagnosisTasks();
      setTasks(response.tasks);
    } finally {
      setLoading(false);
    }
  }, []);

  const fetchTask = useCallback(async () => {
    if (!taskId) {
      setTask(null);
      return;
    }
    setLoading(true);
    try {
      const response = await getDiagnosisTask(taskId);
      setTask(response.task);
    } finally {
      setLoading(false);
    }
  }, [taskId]);

  useEffect(() => {
    fetchTasks();
  }, [fetchTasks]);

  useEffect(() => {
    fetchTask();
  }, [fetchTask]);

  const createTaskHandle = async (request: CreateDiagnosisTaskRequest) => {
    const response = await createDiagnosisTask(request);
    await fetchTasks();
    return response.taskId;
  };

  const startTaskHandle = async (id: string) => {
    const response = await startDiagnosisTask(id);
    await fetchTasks();
    if (taskId === id) {
      setTask(response.task);
    }
    return response.task;
  };

  const confirmTaskHandle = async (id: string, confirmedBy?: string) => {
    await confirmDiagnosisTask(id, confirmedBy);
    await fetchTasks();
    if (taskId === id) {
      await fetchTask();
    }
  };

  return {
    tasks,
    task,
    loading,
    refreshTasks: fetchTasks,
    refreshTask: fetchTask,
    createTask: createTaskHandle,
    startTask: startTaskHandle,
    confirmTask: confirmTaskHandle,
  };
}
