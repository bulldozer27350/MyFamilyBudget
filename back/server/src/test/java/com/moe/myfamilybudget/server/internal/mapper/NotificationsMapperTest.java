package com.moe.myfamilybudget.server.internal.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.api.model.NotificationParametresDto;
import com.moe.myfamilybudget.api.model.NotificationParametresValuesDto;
import com.moe.myfamilybudget.domain.notifications.rules.NotificationSettingsParameters;

/**
 * VT-300 : conversion paramètres de notification <-> DTO OpenAPI, sans Spring.
 */
class NotificationsMapperTest {

    private final NotificationsMapper mapper = new NotificationsMapper();

    private static NotificationSettingsParameters custom() {
        return new NotificationSettingsParameters(true, new BigDecimal("250"), true, new BigDecimal("800"),
                true, true, "23:00", "06:30");
    }

    @Test
    @DisplayName("toValuesDto() reprend tous les champs")
    void mapsAllFields() {
        NotificationParametresValuesDto dto = mapper.toValuesDto(custom());

        assertEquals(Boolean.TRUE, dto.getDebitThresholdEnabled());
        assertEquals(0, new BigDecimal("250").compareTo(dto.getDebitThresholdAmount()));
        assertEquals(Boolean.TRUE, dto.getBalanceFloorEnabled());
        assertEquals(0, new BigDecimal("800").compareTo(dto.getBalanceFloorAmount()));
        assertEquals(Boolean.TRUE, dto.getObjectifReachableEnabled());
        assertEquals(Boolean.TRUE, dto.getQuietHoursEnabled());
        assertEquals("23:00", dto.getQuietHoursStart());
        assertEquals("06:30", dto.getQuietHoursEnd());
    }

    @Test
    @DisplayName("toParametresDto() expose valeurs courantes et valeurs par défaut séparément")
    void mapsCurrentAndDefaults() {
        NotificationParametresDto dto =
                mapper.toParametresDto(custom(), NotificationSettingsParameters.defaults());

        assertEquals(Boolean.TRUE, dto.getValues().getDebitThresholdEnabled());
        assertEquals("23:00", dto.getValues().getQuietHoursStart());
        assertEquals(Boolean.FALSE, dto.getDefaults().getDebitThresholdEnabled());
        assertEquals(0, new BigDecimal("500").compareTo(dto.getDefaults().getDebitThresholdAmount()));
        assertEquals("22:00", dto.getDefaults().getQuietHoursStart());
        assertEquals("07:00", dto.getDefaults().getQuietHoursEnd());
    }

    @Test
    @DisplayName("Aller-retour paramètres -> DTO -> paramètres")
    void roundTrip() {
        NotificationSettingsParameters back = mapper.toParameters(mapper.toValuesDto(custom()));

        assertEquals(Boolean.TRUE, back.debitThresholdEnabled());
        assertEquals(0, new BigDecimal("250").compareTo(back.debitThresholdAmount()));
        assertEquals(Boolean.TRUE, back.balanceFloorEnabled());
        assertEquals(0, new BigDecimal("800").compareTo(back.balanceFloorAmount()));
        assertEquals(Boolean.TRUE, back.objectifReachableEnabled());
        assertEquals(Boolean.TRUE, back.quietHoursEnabled());
        assertEquals("23:00", back.quietHoursStart());
        assertEquals("06:30", back.quietHoursEnd());
    }

    @Test
    @DisplayName("toParameters() : champs absents -> désactivé, montant 0, heures par défaut")
    void missingFieldsUseSafeDefaults() {
        NotificationSettingsParameters p = mapper.toParameters(new NotificationParametresValuesDto());

        assertFalse(p.debitThresholdEnabled());
        assertEquals(0, BigDecimal.ZERO.compareTo(p.debitThresholdAmount()));
        assertFalse(p.balanceFloorEnabled());
        assertEquals(0, BigDecimal.ZERO.compareTo(p.balanceFloorAmount()));
        assertFalse(p.objectifReachableEnabled());
        assertFalse(p.quietHoursEnabled());
        assertEquals("22:00", p.quietHoursStart());
        assertEquals("07:00", p.quietHoursEnd());
    }

    @Test
    @DisplayName("toParameters() : heures vides ou blanches -> heures par défaut")
    void blankTimesFallBackToDefaults() {
        NotificationParametresValuesDto dto = new NotificationParametresValuesDto();
        dto.setQuietHoursStart("");
        dto.setQuietHoursEnd("   ");

        NotificationSettingsParameters p = mapper.toParameters(dto);

        assertEquals("22:00", p.quietHoursStart());
        assertEquals("07:00", p.quietHoursEnd());
    }

    @Test
    @DisplayName("toParameters(null) est refusé (traduit en 400 par l'API)")
    void nullBodyIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> mapper.toParameters(null));
    }

    @Test
    @DisplayName("Le mapping ne valide pas les valeurs : validate() reste à la charge du service")
    void mappingDoesNotValidate() {
        NotificationParametresValuesDto dto = new NotificationParametresValuesDto();
        dto.setDebitThresholdAmount(new BigDecimal("-1"));

        NotificationSettingsParameters p = mapper.toParameters(dto);

        assertEquals(0, new BigDecimal("-1").compareTo(p.debitThresholdAmount()));
        assertThrows(IllegalArgumentException.class, p::validate);
        assertTrue(NotificationSettingsParameters.defaults().debitThresholdAmount().signum() > 0);
    }
}
