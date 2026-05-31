import { useCallback, useEffect, useState } from "react";
import {
  getDiagnosisReport,
  getDiagnosisReports,
  type DiagnosisReportVO,
} from "../api/api.ts";

export function useDiagnosisReports(reportId?: string) {
  const [reports, setReports] = useState<DiagnosisReportVO[]>([]);
  const [report, setReport] = useState<DiagnosisReportVO | null>(null);
  const [loading, setLoading] = useState(false);

  const fetchReports = useCallback(async () => {
    setLoading(true);
    try {
      const response = await getDiagnosisReports();
      setReports(response.reports);
    } finally {
      setLoading(false);
    }
  }, []);

  const fetchReport = useCallback(async () => {
    if (!reportId) {
      setReport(null);
      return;
    }
    setLoading(true);
    try {
      const response = await getDiagnosisReport(reportId);
      setReport(response.report);
    } finally {
      setLoading(false);
    }
  }, [reportId]);

  useEffect(() => {
    fetchReports();
  }, [fetchReports]);

  useEffect(() => {
    fetchReport();
  }, [fetchReport]);

  return {
    reports,
    report,
    loading,
    refreshReports: fetchReports,
    refreshReport: fetchReport,
  };
}
