package com.moe.myfamilybudget.server.internal.mapper;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.api.model.NotificationParametresDto;
import com.moe.myfamilybudget.api.model.NotificationParametresValuesDto;
import com.moe.myfamilybudget.server.internal.notification.NotificationSettingsParameters;

@Component
public class NotificationsMapper {

    public NotificationParametresDto toParametresDto(NotificationSettingsParameters current,
            NotificationSettingsParameters defaults) {
        NotificationParametresDto dto = new NotificationParametresDto();
        dto.setValues(toValuesDto(current));
        dto.setDefaults(toValuesDto(defaults));
        return dto;
    }

    public NotificationParametresValuesDto toValuesDto(NotificationSettingsParameters p) {
        NotificationParametresValuesDto dto = new NotificationParametresValuesDto();
        dto.setDebitThresholdEnabled(p.debitThresholdEnabled());
        dto.setDebitThresholdAmount(p.debitThresholdAmount());
        dto.setBalanceFloorEnabled(p.balanceFloorEnabled());
        dto.setBalanceFloorAmount(p.balanceFloorAmount());
        dto.setObjectifReachableEnabled(p.objectifReachableEnabled());
        return dto;
    }

    /**
     * @throws IllegalArgumentException si le corps est absent (traduit en 400)
     */
    public NotificationSettingsParameters toParameters(NotificationParametresValuesDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Corps de requête manquant.");
        }
        return new NotificationSettingsParameters(
                Boolean.TRUE.equals(dto.getDebitThresholdEnabled()),
                nonNull(dto.getDebitThresholdAmount()),
                Boolean.TRUE.equals(dto.getBalanceFloorEnabled()),
                nonNull(dto.getBalanceFloorAmount()),
                Boolean.TRUE.equals(dto.getObjectifReachableEnabled()));
    }

    private static BigDecimal nonNull(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
