package com.moe.myfamilybudget.web.mapper;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.api.model.NotificationParametresDto;
import com.moe.myfamilybudget.api.model.NotificationParametresValuesDto;
import com.moe.myfamilybudget.api.model.NotificationVerificationResultDto;
import com.moe.myfamilybudget.api.model.PushPublicKeyDto;
import com.moe.myfamilybudget.api.model.PushSubscriptionDto;
import com.moe.myfamilybudget.application.usecase.notifications.NotificationParametersModel;
import com.moe.myfamilybudget.application.usecase.notifications.NotificationParametersSaveCommand;
import com.moe.myfamilybudget.application.usecase.notifications.NotificationParametersValuesModel;
import com.moe.myfamilybudget.application.usecase.notifications.NotificationVerificationResultModel;
import com.moe.myfamilybudget.application.usecase.notifications.PushPublicKeyModel;
import com.moe.myfamilybudget.application.usecase.notifications.PushSubscriptionCommand;
import com.moe.myfamilybudget.domain.notifications.rules.NotificationSettingsParameters;

/**
 * Conversion entre les DTOs OpenAPI (tag Notifications) et les modèles applicatifs
 * de {@code application-api/usecase.notifications} (R-87, R-90, SILO-318, SILO-320).
 */
@Component
public class NotificationsMapper {

    public NotificationParametresDto toParametresDto(NotificationParametersModel model) {
        NotificationParametresDto dto = new NotificationParametresDto();
        dto.setValues(toValuesDto(model.values()));
        dto.setDefaults(toValuesDto(model.defaults()));
        return dto;
    }

    public NotificationParametresDto toParametresDto(NotificationSettingsParameters current,
            NotificationSettingsParameters defaults) {
        NotificationParametresDto dto = new NotificationParametresDto();
        dto.setValues(toValuesDto(current));
        dto.setDefaults(toValuesDto(defaults));
        return dto;
    }

    public NotificationParametresValuesDto toValuesDto(NotificationParametersValuesModel p) {
        NotificationParametresValuesDto dto = new NotificationParametresValuesDto();
        dto.setDebitThresholdEnabled(p.debitThresholdEnabled());
        dto.setDebitThresholdAmount(p.debitThresholdAmount());
        dto.setBalanceFloorEnabled(p.balanceFloorEnabled());
        dto.setBalanceFloorAmount(p.balanceFloorAmount());
        dto.setObjectifReachableEnabled(p.objectifReachableEnabled());
        dto.setQuietHoursEnabled(p.quietHoursEnabled());
        dto.setQuietHoursStart(p.quietHoursStart());
        dto.setQuietHoursEnd(p.quietHoursEnd());
        return dto;
    }

    public NotificationParametresValuesDto toValuesDto(NotificationSettingsParameters p) {
        NotificationParametresValuesDto dto = new NotificationParametresValuesDto();
        dto.setDebitThresholdEnabled(p.debitThresholdEnabled());
        dto.setDebitThresholdAmount(p.debitThresholdAmount());
        dto.setBalanceFloorEnabled(p.balanceFloorEnabled());
        dto.setBalanceFloorAmount(p.balanceFloorAmount());
        dto.setObjectifReachableEnabled(p.objectifReachableEnabled());
        dto.setQuietHoursEnabled(p.quietHoursEnabled());
        dto.setQuietHoursStart(p.quietHoursStart());
        dto.setQuietHoursEnd(p.quietHoursEnd());
        return dto;
    }

    /**
     * @throws IllegalArgumentException si le corps est absent (traduit en 400)
     */
    public NotificationParametersSaveCommand toSaveCommand(NotificationParametresValuesDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Corps de requête manquant.");
        }
        NotificationSettingsParameters defaults = NotificationSettingsParameters.defaults();
        return new NotificationParametersSaveCommand(
                Boolean.TRUE.equals(dto.getDebitThresholdEnabled()),
                nonNull(dto.getDebitThresholdAmount()),
                Boolean.TRUE.equals(dto.getBalanceFloorEnabled()),
                nonNull(dto.getBalanceFloorAmount()),
                Boolean.TRUE.equals(dto.getObjectifReachableEnabled()),
                Boolean.TRUE.equals(dto.getQuietHoursEnabled()),
                nonBlank(dto.getQuietHoursStart(), defaults.quietHoursStart()),
                nonBlank(dto.getQuietHoursEnd(), defaults.quietHoursEnd()));
    }

    /**
     * @throws IllegalArgumentException si le corps est absent (traduit en 400)
     */
    public NotificationSettingsParameters toParameters(NotificationParametresValuesDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Corps de requête manquant.");
        }
        NotificationSettingsParameters defaults = NotificationSettingsParameters.defaults();
        return new NotificationSettingsParameters(
                Boolean.TRUE.equals(dto.getDebitThresholdEnabled()),
                nonNull(dto.getDebitThresholdAmount()),
                Boolean.TRUE.equals(dto.getBalanceFloorEnabled()),
                nonNull(dto.getBalanceFloorAmount()),
                Boolean.TRUE.equals(dto.getObjectifReachableEnabled()),
                Boolean.TRUE.equals(dto.getQuietHoursEnabled()),
                nonBlank(dto.getQuietHoursStart(), defaults.quietHoursStart()),
                nonBlank(dto.getQuietHoursEnd(), defaults.quietHoursEnd()));
    }

    public NotificationVerificationResultDto toVerificationResultDto(NotificationVerificationResultModel model) {
        NotificationVerificationResultDto dto = new NotificationVerificationResultDto();
        dto.setAlertsSent(model.alertsSent());
        return dto;
    }

    public PushPublicKeyDto toPushPublicKeyDto(PushPublicKeyModel model) {
        PushPublicKeyDto dto = new PushPublicKeyDto();
        dto.setPublicKey(model.publicKey());
        return dto;
    }

    public PushSubscriptionCommand toPushSubscriptionCommand(PushSubscriptionDto dto) {
        if (dto == null || dto.getEndpoint() == null || dto.getEndpoint().isBlank()
                || dto.getKeys() == null || dto.getKeys().getP256dh() == null
                || dto.getKeys().getAuth() == null) {
            throw new IllegalArgumentException("Abonnement push incomplet (endpoint et clés requis).");
        }
        return new PushSubscriptionCommand(dto.getEndpoint(), dto.getKeys().getP256dh(), dto.getKeys().getAuth());
    }

    private static BigDecimal nonNull(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static String nonBlank(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }
}
