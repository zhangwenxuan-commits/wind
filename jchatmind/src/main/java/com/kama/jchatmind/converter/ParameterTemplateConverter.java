package com.kama.jchatmind.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.model.dto.ParameterTemplateDTO;
import com.kama.jchatmind.model.entity.ParameterTemplate;
import com.kama.jchatmind.model.request.CreateParameterTemplateRequest;
import com.kama.jchatmind.model.request.UpdateParameterTemplateRequest;
import com.kama.jchatmind.model.vo.ParameterTemplateVO;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
@AllArgsConstructor
public class ParameterTemplateConverter {

    private final ObjectMapper objectMapper;

    public ParameterTemplate toEntity(ParameterTemplateDTO dto) throws JsonProcessingException {
        Assert.notNull(dto, "ParameterTemplateDTO cannot be null");
        return ParameterTemplate.builder()
                .id(dto.getId())
                .name(dto.getName())
                .deviceModel(dto.getDeviceModel())
                .version(dto.getVersion())
                .status(dto.getStatus())
                .referenceShaft(dto.getReferenceShaft())
                .envelopeBandHint(dto.getEnvelopeBandHint())
                .content(dto.getContent() != null ? objectMapper.writeValueAsString(dto.getContent()) : null)
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }

    public ParameterTemplateDTO toDTO(ParameterTemplate entity) throws JsonProcessingException {
        Assert.notNull(entity, "ParameterTemplate cannot be null");
        return ParameterTemplateDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .deviceModel(entity.getDeviceModel())
                .version(entity.getVersion())
                .status(entity.getStatus())
                .referenceShaft(entity.getReferenceShaft())
                .envelopeBandHint(entity.getEnvelopeBandHint())
                .content(entity.getContent() != null
                        ? objectMapper.readValue(entity.getContent(), ParameterTemplateDTO.Content.class)
                        : null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public ParameterTemplateDTO toDTO(CreateParameterTemplateRequest request) {
        return ParameterTemplateDTO.builder()
                .name(request.getName())
                .deviceModel(request.getDeviceModel())
                .status(request.getStatus())
                .referenceShaft(request.getReferenceShaft())
                .envelopeBandHint(request.getEnvelopeBandHint())
                .content(request.getContent())
                .build();
    }

    public void updateDTOFromRequest(ParameterTemplateDTO dto, UpdateParameterTemplateRequest request) {
        if (request.getName() != null) {
            dto.setName(request.getName());
        }
        if (request.getDeviceModel() != null) {
            dto.setDeviceModel(request.getDeviceModel());
        }
        if (request.getStatus() != null) {
            dto.setStatus(request.getStatus());
        }
        if (request.getReferenceShaft() != null) {
            dto.setReferenceShaft(request.getReferenceShaft());
        }
        if (request.getEnvelopeBandHint() != null) {
            dto.setEnvelopeBandHint(request.getEnvelopeBandHint());
        }
        if (request.getContent() != null) {
            dto.setContent(request.getContent());
        }
    }

    public ParameterTemplateVO toVO(ParameterTemplateDTO dto) {
        return ParameterTemplateVO.builder()
                .id(dto.getId())
                .name(dto.getName())
                .deviceModel(dto.getDeviceModel())
                .version(dto.getVersion())
                .status(dto.getStatus())
                .referenceShaft(dto.getReferenceShaft())
                .envelopeBandHint(dto.getEnvelopeBandHint())
                .content(dto.getContent())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }
}
