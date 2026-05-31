package com.kama.jchatmind.controller;

import com.kama.jchatmind.model.common.ApiResponse;
import com.kama.jchatmind.model.response.CreateSignalAssetResponse;
import com.kama.jchatmind.model.response.GetSignalAssetResponse;
import com.kama.jchatmind.model.response.GetSignalAssetsResponse;
import com.kama.jchatmind.service.SignalAssetFacadeService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@AllArgsConstructor
public class SignalAssetController {

    private final SignalAssetFacadeService signalAssetFacadeService;

    @GetMapping("/signal-assets")
    public ApiResponse<GetSignalAssetsResponse> getSignalAssets() {
        return ApiResponse.success(signalAssetFacadeService.getSignalAssets());
    }

    @GetMapping("/signal-assets/{assetId}")
    public ApiResponse<GetSignalAssetResponse> getSignalAsset(@PathVariable String assetId) {
        return ApiResponse.success(signalAssetFacadeService.getSignalAsset(assetId));
    }

    @PostMapping("/signal-assets/upload")
    public ApiResponse<CreateSignalAssetResponse> uploadSignalAsset(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(signalAssetFacadeService.uploadSignalAsset(file));
    }
}
