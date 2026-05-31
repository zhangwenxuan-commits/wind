package com.kama.jchatmind.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kama.jchatmind.converter.ParameterTemplateConverter;
import com.kama.jchatmind.exception.BizException;
import com.kama.jchatmind.mapper.ParameterTemplateMapper;
import com.kama.jchatmind.model.dto.ParameterTemplateDTO;
import com.kama.jchatmind.model.entity.ParameterTemplate;
import com.kama.jchatmind.model.request.CreateParameterTemplateRequest;
import com.kama.jchatmind.model.request.UpdateParameterTemplateRequest;
import com.kama.jchatmind.model.response.CreateParameterTemplateResponse;
import com.kama.jchatmind.model.response.GetParameterTemplateResponse;
import com.kama.jchatmind.model.response.GetParameterTemplatesResponse;
import com.kama.jchatmind.model.vo.ParameterTemplateVO;
import com.kama.jchatmind.service.ParameterTemplateFacadeService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class ParameterTemplateFacadeServiceImpl implements ParameterTemplateFacadeService {

    private final ParameterTemplateMapper parameterTemplateMapper;
    private final ParameterTemplateConverter parameterTemplateConverter;

    @Override
    public GetParameterTemplatesResponse getParameterTemplates() {
        List<ParameterTemplateVO> result = new ArrayList<>();
        for (ParameterTemplate template : parameterTemplateMapper.selectAll()) {
            try {
                result.add(parameterTemplateConverter.toVO(parameterTemplateConverter.toDTO(template)));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        return GetParameterTemplatesResponse.builder()
                .templates(result.toArray(new ParameterTemplateVO[0]))
                .build();
    }

    @Override
    public GetParameterTemplateResponse getParameterTemplate(String templateId) {
        ParameterTemplate template = requireTemplate(templateId);
        try {
            return GetParameterTemplateResponse.builder()
                    .template(parameterTemplateConverter.toVO(parameterTemplateConverter.toDTO(template)))
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public CreateParameterTemplateResponse createParameterTemplate(CreateParameterTemplateRequest request) {
        validateRequest(request.getName());
        try {
            ParameterTemplateDTO dto = parameterTemplateConverter.toDTO(request);
            dto.setVersion(1);
            dto.setStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : "ACTIVE");
            LocalDateTime now = LocalDateTime.now();
            dto.setCreatedAt(now);
            dto.setUpdatedAt(now);
            ParameterTemplate entity = parameterTemplateConverter.toEntity(dto);
            int result = parameterTemplateMapper.insert(entity);
            if (result <= 0) {
                throw new BizException("创建参数模板失败");
            }
            return CreateParameterTemplateResponse.builder()
                    .templateId(entity.getId())
                    .build();
        } catch (JsonProcessingException e) {
            throw new BizException("创建参数模板时发生序列化错误: " + e.getMessage());
        }
    }

    @Override
    public void updateParameterTemplate(String templateId, UpdateParameterTemplateRequest request) {
        ParameterTemplate existing = requireTemplate(templateId);
        try {
            ParameterTemplateDTO dto = parameterTemplateConverter.toDTO(existing);
            parameterTemplateConverter.updateDTOFromRequest(dto, request);
            dto.setUpdatedAt(LocalDateTime.now());
            int result = parameterTemplateMapper.updateById(parameterTemplateConverter.toEntity(dto));
            if (result <= 0) {
                throw new BizException("更新参数模板失败");
            }
        } catch (JsonProcessingException e) {
            throw new BizException("更新参数模板时发生序列化错误: " + e.getMessage());
        }
    }

    private ParameterTemplate requireTemplate(String templateId) {
        ParameterTemplate template = parameterTemplateMapper.selectById(templateId);
        if (template == null) {
            throw new BizException("参数模板不存在: " + templateId);
        }
        return template;
    }

    private void validateRequest(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BizException("参数模板名称不能为空");
        }
    }
}
