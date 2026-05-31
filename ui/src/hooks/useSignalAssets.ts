import { useCallback, useEffect, useState } from "react";
import {
  getSignalAssets,
  type SignalAssetVO,
  uploadSignalAsset,
} from "../api/api.ts";

export function useSignalAssets() {
  const [assets, setAssets] = useState<SignalAssetVO[]>([]);
  const [loading, setLoading] = useState(false);

  const fetchAssets = useCallback(async () => {
    setLoading(true);
    try {
      const response = await getSignalAssets();
      setAssets(response.assets);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchAssets();
  }, [fetchAssets]);

  const uploadAssetHandle = async (file: File) => {
    const response = await uploadSignalAsset(file);
    await fetchAssets();
    return response.assetId;
  };

  return {
    assets,
    loading,
    refreshAssets: fetchAssets,
    uploadAsset: uploadAssetHandle,
  };
}
