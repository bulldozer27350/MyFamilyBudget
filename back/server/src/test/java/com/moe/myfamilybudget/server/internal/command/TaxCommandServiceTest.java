package com.moe.myfamilybudget.server.internal.command;

import com.moe.myfamilybudget.application.command.TaxCommandService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.domain.tax.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.domain.tax.model.TaxBracketModel;
import com.moe.myfamilybudget.domain.tax.model.TaxChildModel;
import com.moe.myfamilybudget.domain.tax.model.TaxRateOverrideModel;
import com.moe.myfamilybudget.domain.tax.port.TaxSettingField;
import com.moe.myfamilybudget.domain.tax.port.TaxWriter;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryTaxStore;
import com.moe.myfamilybudget.server.internal.testsupport.RecordingTransactionRunner;

/**
 * DB-021 -- Le command service Fiscalite valide la commande, delegue au port d'ecriture et laisse l'erreur
 * de persistance remonter telle quelle ; l'adaptateur ecrit bien dans le modele relu par les lecteurs.
 *
 * <p>R-42 (lot B, meme gabarit que {@code TresorerieCommandServiceTest}) : chaque commande s'execute desormais
 * dans une transaction, apres prise du verrou du silo Fiscalite.
 */
@DisplayName("DB-021 / R-42 -- TaxCommandService")
class TaxCommandServiceTest {

    private static final TaxChildModel CHILD = new TaxChildModel("tc_1", "Emma", 2015);
    private static final List<TaxBracketModel> BRACKETS = List.of(
            new TaxBracketModel("tb_a", new BigDecimal("10000"), new BigDecimal("0")),
            new TaxBracketModel("tb_b", null, new BigDecimal("0.30")));
    private static final TaxRateOverrideModel RATE_OVERRIDE = new TaxRateOverrideModel(2027, new BigDecimal("0.05"));
    private static final TaxActualOverrideModel ACTUAL_OVERRIDE = new TaxActualOverrideModel(2026,
            new BigDecimal("1800"));

    private TaxWriter writer;
    private TaxCommandService service;

    @BeforeEach
    void setUp() {
        writer = mock(TaxWriter.class);
        service = new TaxCommandService(writer, silos -> { }, RecordingTransactionRunner.direct());
    }

    // --- succes : delegation au port ---

    @Test
    @DisplayName("succes : updateTaxConfig transmet les quatre listes au port")
    void updateTaxConfigDelegates() {
        service.updateTaxConfig(List.of(CHILD), BRACKETS, List.of(RATE_OVERRIDE), List.of(ACTUAL_OVERRIDE));

        verify(writer).updateTaxConfig(List.of(CHILD), BRACKETS, List.of(RATE_OVERRIDE), List.of(ACTUAL_OVERRIDE));
    }

    @Test
    @DisplayName("succes : updateTaxConfig transmet les listes null (conservation de l'existant)")
    void updateTaxConfigKeepsNullsForWriter() {
        service.updateTaxConfig(null, null, null, null);

        verify(writer).updateTaxConfig(null, null, null, null);
    }

    @Test
    @DisplayName("succes : updateTaxSettings et resetDefaultTaxBrackets sont delegues")
    void otherCommandsDelegate() {
        service.updateTaxSettings(TaxSettingField.CHILD_EXIT_AGE, 18);
        service.resetDefaultTaxBrackets();

        verify(writer).updateTaxSettings(TaxSettingField.CHILD_EXIT_AGE, 18);
        verify(writer).resetDefaultTaxBrackets();
    }

    // --- validation ---

    @Test
    @DisplayName("DB-050 : TaxSettingField.find respecte la casse et ignore les champs inconnus")
    void settingFieldFind() {
        assertThat(TaxSettingField.find("childExitAge")).contains(TaxSettingField.CHILD_EXIT_AGE);
        assertThat(TaxSettingField.find("taxAbattement")).contains(TaxSettingField.TAX_ABATTEMENT);
        assertThat(TaxSettingField.find("CHILDEXITAGE")).isEmpty();
        assertThat(TaxSettingField.find("inconnu")).isEmpty();
        // SET-030 : les parametres des autres owners ne sont plus des parametres fiscaux.
        assertThat(TaxSettingField.find("retireAge")).isEmpty();
        assertThat(TaxSettingField.find("pivotBalanceManual")).isEmpty();
        assertThat(TaxSettingField.values()).containsExactly(TaxSettingField.CHILD_EXIT_AGE, TaxSettingField.TAX_ABATTEMENT);
        assertThat(TaxSettingField.find(null)).isEmpty();
    }

    @Test
    @DisplayName("validation : un nom de parametre null est refuse et rien n'est ecrit")
    void nullFieldIsRejectedWithoutWriting() {
        assertThatThrownBy(() -> service.updateTaxSettings(null, 18))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(writer);
    }

    // --- propagation d'erreur ---

    @Test
    @DisplayName("erreur : l'exception du port est propagee telle quelle pour chaque commande")
    void writerFailuresArePropagated() {
        IllegalStateException dbDown = new IllegalStateException("database down");
        doThrow(dbDown).when(writer).updateTaxConfig(any(), any(), any(), any());
        doThrow(dbDown).when(writer).updateTaxSettings(any(TaxSettingField.class), any());
        doThrow(dbDown).when(writer).resetDefaultTaxBrackets();

        assertThatThrownBy(() -> service.updateTaxConfig(List.of(CHILD), null, null, null)).isSameAs(dbDown);
        assertThatThrownBy(() -> service.updateTaxSettings(TaxSettingField.CHILD_EXIT_AGE, 18)).isSameAs(dbDown);
        assertThatThrownBy(() -> service.resetDefaultTaxBrackets()).isSameAs(dbDown);
    }

    // --- integration avec l'adaptateur ---

    @Test
    @DisplayName("integration adaptateur : configuration, bareme et parametre relus par les lecteurs")
    void adapterWritesAreReadBackByReaders() {
        InMemoryTaxStore store = new InMemoryTaxStore();
        TaxCommandService realService = new TaxCommandService(store, silos -> { }, RecordingTransactionRunner.direct());

        realService.updateTaxConfig(List.of(CHILD), BRACKETS, List.of(RATE_OVERRIDE), List.of(ACTUAL_OVERRIDE));
        realService.updateTaxSettings(TaxSettingField.CHILD_EXIT_AGE, 18);

        assertThat(store.getTaxChildren()).containsExactly(CHILD);
        assertThat(store.getTaxBrackets()).containsExactlyElementsOf(BRACKETS);
        assertThat(store.getTaxRateOverrides()).containsExactly(RATE_OVERRIDE);
        assertThat(store.getTaxActualOverrides()).containsExactly(ACTUAL_OVERRIDE);
        assertThat(store.getTaxSettings().childExitAge()).isEqualTo(18);

        realService.resetDefaultTaxBrackets();

        assertThat(store.getTaxBrackets()).hasSize(5);
        assertThat(store.getTaxChildren()).containsExactly(CHILD);
    }
}
