package com.moe.myfamilybudget.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.application.usecase.analysis.AnalyseResultModel;
import com.moe.myfamilybudget.application.usecase.analysis.AnalysisUseCase;
import com.moe.myfamilybudget.domain.analysis.core.DefaultAnalyseCalculationService;
import com.moe.myfamilybudget.server.internal.adapter.PatrimoinePersistenceAdapter;
import com.moe.myfamilybudget.server.internal.testsupport.CacheBackedAppSettings;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryBankStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryRetirementStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryTaxStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryTreasuryStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryWealthStore;

/**
 * R-84 (même gabarit que {@code DefaultTaxUseCaseTest}, SILO-315) : le cas d'usage compose
 * l'analyse Réel vs Prévisionnel sans {@code ResponseEntity} ni DTO, et traduit les types du silo
 * Analyse vers les modèles de {@code application-api}.
 */
class DefaultAnalysisUseCaseTest {

    private final InMemoryBankStore bankStore = new InMemoryBankStore();

    private AnalysisUseCase useCase;

    @BeforeEach
    void setUp() {
        InMemoryRetirementStore retirementStore = new InMemoryRetirementStore();
        InMemoryTaxStore taxStore = new InMemoryTaxStore();
        InMemoryTreasuryStore treasuryStore = new InMemoryTreasuryStore();
        CacheBackedAppSettings appSettings = new CacheBackedAppSettings();
        PatrimoinePersistenceAdapter patrimoineAdapter = new PatrimoinePersistenceAdapter(
                new InMemoryWealthStore().getDelegate(), new InMemoryTreasuryStore().getDelegate());

        useCase = new DefaultAnalysisUseCase(
                new DefaultAnalyseCalculationService(),
                retirementStore,
                taxStore,
                treasuryStore,
                appSettings,
                appSettings,
                treasuryStore,
                patrimoineAdapter,
                bankStore);
    }

    @Test
    @DisplayName("getAnalyse() renvoie un modele complet : kpis, atterrissage, derives, comparatif et syntheses")
    void returnsACompleteResult() {
        AnalyseResultModel result = useCase.getAnalyse(12);

        assertThat(result).isNotNull();
        assertThat(result.kpis()).isNotNull();
        assertThat(result.landingData()).isNotNull();
        assertThat(result.driftRows()).isNotNull();
        assertThat(result.monthlyCompareData()).isNotNull();
        assertThat(result.categorySummaries()).isNotNull();
        assertThat(result.currentMonthISO()).isNotNull();
        assertThat(result.currentMonthLabel()).isNotNull();
    }

    @Test
    @DisplayName("getAnalyse(null) applique la fenetre par defaut de 12 mois (pas d'exception)")
    void appliesDefaultWindowWhenMonthsBackIsNull() {
        AnalyseResultModel result = useCase.getAnalyse(null);

        assertThat(result).isNotNull();
        assertThat(result.kpis()).isNotNull();
    }
}
