import { useCallback, useEffect, useState } from "react";
import {
  getAnalysisEvidenceByRunId,
  getAnalysisRunsByTaskId,
  type AnalysisEvidenceVO,
  type AnalysisRunVO,
} from "../api/api.ts";

export function useAnalysisRuns(taskId?: string, runId?: string) {
  const [runs, setRuns] = useState<AnalysisRunVO[]>([]);
  const [evidence, setEvidence] = useState<AnalysisEvidenceVO[]>([]);
  const [loading, setLoading] = useState(false);

  const fetchRuns = useCallback(async () => {
    if (!taskId) {
      setRuns([]);
      return;
    }
    setLoading(true);
    try {
      const response = await getAnalysisRunsByTaskId(taskId);
      setRuns(response.runs);
    } finally {
      setLoading(false);
    }
  }, [taskId]);

  const fetchEvidence = useCallback(async () => {
    if (!runId) {
      setEvidence([]);
      return;
    }
    setLoading(true);
    try {
      const response = await getAnalysisEvidenceByRunId(runId);
      setEvidence(response.evidence);
    } finally {
      setLoading(false);
    }
  }, [runId]);

  useEffect(() => {
    fetchRuns();
  }, [fetchRuns]);

  useEffect(() => {
    fetchEvidence();
  }, [fetchEvidence]);

  return {
    runs,
    evidence,
    loading,
    refreshRuns: fetchRuns,
    refreshEvidence: fetchEvidence,
  };
}
