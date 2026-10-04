package com.moe.myfamilybudget.server.internal.calculation;

import com.moe.myfamilybudget.application.settings.ObjectifsSettingsService;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.server.internal.testsupport.InMemoryObjectifsSettingsStore;
import com.moe.myfamilybudget.domain.goals.calculation.ObjectifsParameters;

class ObjectifsSettingsServiceTest {

    private ObjectifsSettingsService service;

    @BeforeEach
    void setUp() {
        service = new ObjectifsSettingsService(new InMemoryObjectifsSettingsStore());
    }

    @Test
    @DisplayName("Sans enregistrement : valeurs brutes nulles, valeurs effectives par défaut (12 et 3 mois)")
    void testDefaults() {
        ObjectifsParameters current = service.current();

        assertThat(current.secureHorizonMonths()).isNull();
        assertThat(current.liquidHorizonMonths()).isNull();
        assertThat(current.getEffectiveSecureHorizonMonths()).isEqualTo(12);
        assertThat(current.getEffectiveLiquidHorizonMonths()).isEqualTo(3);
    }

    @Test
    @DisplayName("updateField() modifie un seul seuil à la fois et accepte nombre ou chaîne numérique")
    void testUpdateFieldOneAtATime() {
        service.updateField("goalSecureHorizonMonths", 24);
        service.updateField("goalLiquidHorizonMonths", " 6 ");

        ObjectifsParameters current = service.current();
        assertThat(current.secureHorizonMonths()).isEqualTo(24);
        assertThat(current.liquidHorizonMonths()).isEqualTo(6);

        service.updateField("goalSecureHorizonMonths", new BigDecimal("18"));
        assertThat(service.current().secureHorizonMonths()).isEqualTo(18);
        assertThat(service.current().liquidHorizonMonths()).isEqualTo(6);
    }

    @Test
    @DisplayName("Une valeur nulle ou illisible efface le seuil (retour au défaut effectif)")
    void testUnreadableValueClearsThreshold() {
        service.updateField("goalSecureHorizonMonths", 24);

        service.updateField("goalSecureHorizonMonths", "abc");
        assertThat(service.current().secureHorizonMonths()).isNull();
        assertThat(service.current().getEffectiveSecureHorizonMonths()).isEqualTo(12);

        service.updateField("goalLiquidHorizonMonths", 5);
        service.updateField("goalLiquidHorizonMonths", null);
        assertThat(service.current().liquidHorizonMonths()).isNull();
    }

    @Test
    @DisplayName("owns() ne reconnaît que les deux seuils du domaine Objectifs")
    void testOwns() {
        assertThat(ObjectifsSettingsService.owns("goalSecureHorizonMonths")).isTrue();
        assertThat(ObjectifsSettingsService.owns("goalLiquidHorizonMonths")).isTrue();
        assertThat(ObjectifsSettingsService.owns("inflationRate")).isFalse();
        assertThat(ObjectifsSettingsService.owns(null)).isFalse();
    }

    @Test
    @DisplayName("reset() supprime les seuils enregistrés")
    void testReset() {
        service.save(new ObjectifsParameters(30, 9));

        service.reset();

        assertThat(service.current()).isEqualTo(ObjectifsParameters.defaults());
    }
}
