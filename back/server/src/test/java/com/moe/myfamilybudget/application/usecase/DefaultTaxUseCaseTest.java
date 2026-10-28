package com.moe.myfamilybudget.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.application.command.TaxCommandService;
import com.moe.myfamilybudget.application.factory.RetirementInputFactory;
import com.moe.myfamilybudget.application.usecase.tax.TaxBracketModel;
import com.moe.myfamilybudget.application.usecase.tax.TaxChildModel;
import com.moe.myfamilybudget.application.usecase.tax.TaxConfigSaveCommand;
import com.moe.myfamilybudget.application.usecase.tax.TaxResultModel;
import com.moe.myfamilybudget.application.usecase.tax.TaxUseCase;
import com.moe.myfamilybudget.domain.retirement.core.DefaultRetirementCalculationService;
import com.moe.myfamilybudget.domain.tax.core.DefaultTaxCalculationService;
import com.moe.myfamilybudget.server.internal.adapter.PatrimoinePersistenceAdapter;
import com.moe.myfamilybudget.server.internal.testsupport.CacheBackedAppSettings;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryBankStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryRetirementStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryTaxStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryTreasuryStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryWealthStore;
import com.moe.myfamilybudget.server.internal.testsupport.RecordingTransactionRunner;

/**
 * R-80 (meme gabarit que {@code DefaultRetirementUseCaseTest}, SILO-311) : le cas d'usage compose la
 * reponse Fiscalite sans {@code ResponseEntity} ni DTO, et traduit les types des silos vers les modeles
 * de {@code application-api}.
 */
class DefaultTaxUseCaseTest {

    private final InMemoryBankStore bankStore = new InMemoryBankStore();

    private TaxUseCase useCase;
    private InMemoryTaxStore taxStore;

    @BeforeEach
    void setUp() {
        taxStore = new InMemoryTaxStore();
        InMemoryRetirementStore retirementStore = new InMemoryRetirementStore();
        InMemoryTreasuryStore treasuryStore = new InMemoryTreasuryStore();
        CacheBackedAppSettings appSettings = new CacheBackedAppSettings();
        TaxCommandService taxCommandService = new TaxCommandService(taxStore, silos -> { },
                RecordingTransactionRunner.direct());

        useCase = new DefaultTaxUseCase(
                new RetirementInputFactory(),
                new DefaultRetirementCalculationService(),
                new DefaultTaxCalculationService(),
                taxCommandService,
                retirementStore,
                taxStore,
                treasuryStore,
                appSettings,
                appSettings,
                taxStore,
                treasuryStore,
                new PatrimoinePersistenceAdapter(new InMemoryWealthStore().getDelegate(), new InMemoryTreasuryStore().getDelegate()),
                retirementStore,
                bankStore);
    }

    @Test
    @DisplayName("getImpots() renvoie un modele complet : enfants, bareme, surcharges, parametres et simulation")
    void returnsACompleteResult() {
        TaxResultModel result = useCase.getImpots();

        assertThat(result).isNotNull();
        assertThat(result.taxChildren()).isNotNull();
        assertThat(result.taxBrackets()).isNotEmpty();
        assertThat(result.taxRateOverrides()).isNotNull();
        assertThat(result.taxActualOverrides()).isNotNull();
        assertThat(result.settings()).isNotNull();
        assertThat(result.taxPreview()).isNotNull();
    }

    @Test
    @DisplayName("saveTaxConfig() ecrit la commande puis getImpots() la relit")
    void savesACommandAndRereadsIt() {
        useCase.saveTaxConfig(new TaxConfigSaveCommand(
                List.of(new TaxChildModel("tc1", "Alice", 2018)),
                List.of(new TaxBracketModel("tb1", new BigDecimal("12000"), BigDecimal.ZERO),
                        new TaxBracketModel("tb2", null, new BigDecimal("0.11"))),
                null,
                null));

        TaxResultModel result = useCase.getImpots();

        assertThat(result.taxChildren()).hasSize(1);
        assertThat(result.taxChildren().get(0).name()).isEqualTo("Alice");
        assertThat(result.taxChildren().get(0).birthYear()).isEqualTo(2018);
        assertThat(result.taxBrackets()).hasSize(2);
    }

    @Test
    @DisplayName("saveTaxConfig(null) est refuse sans ecriture")
    void refusesANullCommand() {
        assertThatThrownBy(() -> useCase.saveTaxConfig(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("resetDefaultTaxBrackets() reinitialise le bareme d'imposition")
    void resetsDefaultTaxBrackets() {
        useCase.saveTaxConfig(new TaxConfigSaveCommand(null,
                List.of(new TaxBracketModel("tbX", new BigDecimal("1"), BigDecimal.ZERO)), null, null));

        useCase.resetDefaultTaxBrackets();

        TaxResultModel result = useCase.getImpots();
        assertThat(result.taxBrackets()).isNotEmpty();
        assertThat(result.taxBrackets().get(0).id()).isNotEqualTo("tbX");
    }
}
