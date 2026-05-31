import { useCallback, useEffect, useState } from "react";
import {
  createParameterTemplate,
  getParameterTemplates,
  updateParameterTemplate,
  type CreateParameterTemplateRequest,
  type ParameterTemplateVO,
  type UpdateParameterTemplateRequest,
} from "../api/api.ts";

export function useParameterTemplates() {
  const [parameterTemplates, setParameterTemplates] = useState<
    ParameterTemplateVO[]
  >([]);
  const [loading, setLoading] = useState(false);

  const fetchParameterTemplates = useCallback(async () => {
    setLoading(true);
    try {
      const response = await getParameterTemplates();
      setParameterTemplates(response.templates);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchParameterTemplates();
  }, [fetchParameterTemplates]);

  const createParameterTemplateHandle = async (
    request: CreateParameterTemplateRequest,
  ) => {
    const response = await createParameterTemplate(request);
    await fetchParameterTemplates();
    return response.templateId;
  };

  const updateParameterTemplateHandle = async (
    templateId: string,
    request: UpdateParameterTemplateRequest,
  ) => {
    await updateParameterTemplate(templateId, request);
    await fetchParameterTemplates();
  };

  return {
    parameterTemplates,
    loading,
    refreshParameterTemplates: fetchParameterTemplates,
    createParameterTemplate: createParameterTemplateHandle,
    updateParameterTemplate: updateParameterTemplateHandle,
  };
}
