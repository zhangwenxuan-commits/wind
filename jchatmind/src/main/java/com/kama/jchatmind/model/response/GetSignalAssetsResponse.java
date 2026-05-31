package com.kama.jchatmind.model.response;

import com.kama.jchatmind.model.vo.SignalAssetVO;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GetSignalAssetsResponse {
    private SignalAssetVO[] assets;
}
