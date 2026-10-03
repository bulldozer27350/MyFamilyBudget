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
import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.persistence.adapter.SettingsPersistenceAdapter;
import com.moe.myfamilybudget.persistence.adapter.TaxPersistenceAdapter;
import com.moe.myfamilybudget.domain.tax.port.TaxSettingField;
import com.moe.myfamilybudget.domain.tax.port.TaxWriter;
import com.moe.myfamilybudget.server.internal.testsupport.PersistenceManagerTestFactory;

/**
 * DB-021 -- Le command service Fiscalite valide la commande, delegue au port d'ecriture et laisse l'erreur
 * de persistance remonter telle quelle ; l'adaptateur ecrit bien dans le modele relu par les lecteurs.
 */
@DisplayName("DB-021 -- TaxCommandService")
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
        service = new TaxCommandService(writer);
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
        PersistenceManager persistenceManager = PersistenceManagerTestFactory.inMemory();
        persistenceManager.init();
        TaxPersistenceAdapter adapter = new TaxPersistenceAdapter(persistenceManager);
        SettingsPersistenceAdapter settings = new SettingsPersistenceAdapter(persistenceManager);
        TaxCommandService realService = new TaxCommandService(adapter);

        realService.updateTaxConfig(List.of(CHILD), BRACKETS, List.of(RATE_OVERRIDE), List.of(ACTUAL_OVERRIDE));
        realService.updateTaxSettings(TaxSettingField.CHILD_EXIT_AGE, 18);

        assertThat(adapter.getTaxChildren()).containsExactly(CHILD);
        assertThat(adapter.getTaxBrackets()).containsExactlyElementsOf(BRACKETS);
        assertThat(adapter.getTaxRateOverrides()).containsExactly(RATE_OVERRIDE);
        assertThat(adapter.getTaxActualOverrides()).containsExactly(ACTUAL_OVERRIDE);
        assertThat(settings.getSettings().childExitAge()).isEqualTo(18);

        realService.resetDefaultTaxBrackets();

        assertThat(adapter.getTaxBrackets()).hasSize(5);
        assertThat(adapter.getTaxChildren()).containsExactly(CHILD);
    }
}
