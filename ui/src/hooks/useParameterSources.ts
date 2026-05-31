import { useCallback, useEffect, useState } from "react";
import {
  getParameterSources,
  type ParameterSourceVO,
} from "../api/api.ts";

export function useParameterSources() {
  const [parameterSources, setParameterSources] = useState<ParameterSourceVO[]>(
    [],
  );
  const [loading, setLoading] = useState(false);

  const fetchParameterSources = useCallback(async () => {
    setLoading(true);
    try {
      const response = await getParameterSources();
      setParameterSources(response.parameterSources);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchParameterSources();
  }, [fetchParameterSources]);

  return {
    parameterSources,
    loading,
    refreshParameterSources: fetchParameterSources,
  };
}
