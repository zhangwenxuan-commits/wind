package com.kama.jchatmind.service;

import com.kama.jchatmind.model.response.CreateSignalAssetResponse;
import com.kama.jchatmind.model.response.GetSignalAssetResponse;
import com.kama.jchatmind.model.response.GetSignalAssetsResponse;
import org.springframework.web.multipart.MultipartFile;

public interface SignalAssetFacadeService {
    GetSignalAssetsResponse getSignalAssets();

    GetSignalAssetResponse getSignalAsset(String assetId);

    CreateSignalAssetResponse uploadSignalAsset(MultipartFile file);
}
