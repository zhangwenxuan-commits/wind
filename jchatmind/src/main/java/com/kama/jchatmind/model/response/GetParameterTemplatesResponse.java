package com.kama.jchatmind.model.response;

import com.kama.jchatmind.model.vo.ParameterTemplateVO;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GetParameterTemplatesResponse {
    private ParameterTemplateVO[] templates;
}
