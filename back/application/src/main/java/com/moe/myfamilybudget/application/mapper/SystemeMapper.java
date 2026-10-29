package com.moe.myfamilybudget.application.mapper;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moe.myfamilybudget.api.model.BudgetDataDto;

/**
 * Mapper pour les opérations système : conversion entre {@link BudgetDataDto} (OpenAPI DTO)
 * et {@code Map<String, Object>} (modèle applicatif de {@code application-api/usecase.system}) (R-88, SILO-319).
 */
@Component
public class SystemeMapper {

    private final ObjectMapper objectMapper;

    public SystemeMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public BudgetDataDto toDto(Map<String, Object> map) {
        return map == null ? null : objectMapper.convertValue(map, BudgetDataDto.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> toMap(BudgetDataDto dto) {
        return dto == null ? null : objectMapper.convertValue(dto, Map.class);
    }
}
