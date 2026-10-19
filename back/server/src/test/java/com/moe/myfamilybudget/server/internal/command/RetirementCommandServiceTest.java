package com.moe.myfamilybudget.server.internal.command;

import com.moe.myfamilybudget.application.command.RetirementCommandService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingField;
import com.moe.myfamilybudget.domain.retirement.port.RetirementWriter;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryRetirementStore;
import com.moe.myfamilybudget.server.internal.testsupport.RecordingTransactionRunner;

/**
 * DB-020 -- Le command service Retraite valide la commande, delegue au port d'ecriture et laisse l'erreur
 * de persistance remonter telle quelle ; l'adaptateur ecrit bien dans le modele relu par les lecteurs.
 *
 * <p>R-40 (lot B, meme gabarit que {@code TaxCommandServiceTest}/{@code TresorerieCommandServiceTest}) :
 * chaque commande s'execute desormais dans une transaction, apres prise du verrou du silo Retraite.
 */
@DisplayName("DB-020 / R-40 -- RetirementCommandService")
class RetirementCommandServiceTest {

    private static final RetirementModel RETIREMENT = new RetirementModel(
            List.of(new RetirementModel.RetirementPersonModel("p_1", "Alice", 1990, "Salaire", 140, "2025-12-31",
                    List.of(new RetirementModel.SalaryHistoryModel(2025, new BigDecimal("36000"))),
                    new BigDecimal("2500"), new BigDecimal("0.0051"), null)),
            new BigDecimal("47100"), new BigDecimal("0.015"), new BigDecimal("1.4386"), "2025-01-01",
            new BigDecimal("0.01"));

    private RetirementWriter writer;
    private RetirementCommandService service;

    @BeforeEach
    void setUp() {
        writer = mock(RetirementWriter.class);
        service = new RetirementCommandService(writer, silos -> { }, RecordingTransactionRunner.direct());
    }

    @Test
    @DisplayName("SET-020 : un parametre de retraite est transmis une fois au port d'ecriture")
    void updateRetirementSettingDelegatesToWriter() {
        service.updateRetirementSetting(RetirementSettingField.RETIRE_AGE, 62);

        verify(writer).updateRetirementSetting(RetirementSettingField.RETIRE_AGE, 62);
    }

    @Test
    @DisplayName("SET-020 : un parametre de retraite null est refuse et rien n'est ecrit")
    void nullRetirementSettingIsRejectedWithoutWriting() {
        assertThatThrownBy(() -> service.updateRetirementSetting(null, 62))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(writer);
    }

    @Test
    @DisplayName("SET-020 : l'ecriture d'un parametre de retraite est relue par RetirementSettingsReader")
    void retirementSettingIsReadBackThroughSettingsReader() {
        InMemoryRetirementStore store = new InMemoryRetirementStore();

        new RetirementCommandService(store, silos -> { }, RecordingTransactionRunner.direct())
                .updateRetirementSetting(RetirementSettingField.RETIRE_AGE, 62);

        assertThat(store.getRetirementSettings().retireAge()).isEqualTo(62);
    }

    @Test
    @DisplayName("succes : la commande est transmise une fois au port d'ecriture")
    void updateDelegatesToWriter() {
        service.updateRetirement(RETIREMENT);

        verify(writer).updateRetirement(RETIREMENT);
    }

    @Test
    @DisplayName("validation : un modele null est refuse et rien n'est ecrit")
    void nullRetirementIsRejectedWithoutWriting() {
        assertThatThrownBy(() -> service.updateRetirement(null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(writer);
    }

    @Test
    @DisplayName("erreur : l'exception du port d'ecriture est propagee telle quelle")
    void writerFailureIsPropagated() {
        IllegalStateException dbDown = new IllegalStateException("database down");
        doThrow(dbDown).when(writer).updateRetirement(any());

        assertThatThrownBy(() -> service.updateRetirement(RETIREMENT)).isSameAs(dbDown);
    }

    @Test
    @DisplayName("erreur : une validation en echec n'appelle jamais le port")
    void invalidCommandNeverReachesWriter() {
        assertThatThrownBy(() -> service.updateRetirement(null)).isInstanceOf(RuntimeException.class);

        verify(writer, never()).updateRetirement(any());
    }

    @Test
    @DisplayName("integration adaptateur : l'ecriture est relue par les ports de lecture")
    void adapterWriteIsReadBackByReaders() {
        InMemoryRetirementStore store = new InMemoryRetirementStore();
        assertThat(store.getRetirement().people()).isEmpty();

        new RetirementCommandService(store, silos -> { }, RecordingTransactionRunner.direct())
                .updateRetirement(RETIREMENT);

        assertThat(store.getRetirement()).isEqualTo(RETIREMENT);
    }
}
