package com.moe.myfamilybudget.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.moe.myfamilybudget.api.model.AnalyseResponseDto;
import com.moe.myfamilybudget.web.mapper.AnalyseMapper;
import com.moe.myfamilybudget.application.usecase.DefaultAnalysisUseCase;
import com.moe.myfamilybudget.domain.analysis.core.DefaultAnalyseCalculationService;
import com.moe.myfamilybudget.application.settings.ObjectifsSettingsService;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryObjectifsSettingsStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryBankStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryTreasuryStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryGoalStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryLoanStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryWealthStore;
import com.moe.myfamilybudget.server.internal.adapter.PatrimoinePersistenceAdapter;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryRetirementStore;
import com.moe.myfamilybudget.server.internal.testsupport.CacheBackedAppSettings;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryTaxStore;

/**
 * R-84 : {@code AnalyseServiceImpl} ne garde plus que la conversion HTTP et la composition du
 * budget réexposé ({@code data}) ; le calcul de l'analyse passe par {@link DefaultAnalysisUseCase}
 * (voir {@code DefaultAnalysisUseCaseTest} pour la couverture détaillée du cas d'usage).
 */
class AnalyseServiceImplTest {

    private final InMemoryBankStore bankStore = new InMemoryBankStore();

    private AnalyseServiceImpl service;
    private AnalyseMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new AnalyseMapper();
        CacheBackedAppSettings appSettings = new CacheBackedAppSettings();
        InMemoryRetirementStore retirementStore = new InMemoryRetirementStore();
        InMemoryTaxStore taxStore = new InMemoryTaxStore();
        InMemoryTreasuryStore treasuryStore = new InMemoryTreasuryStore();
        PatrimoinePersistenceAdapter patrimoineAdapter = new PatrimoinePersistenceAdapter(
                new InMemoryWealthStore().getDelegate(), new InMemoryTreasuryStore().getDelegate());

        DefaultAnalysisUseCase analysisUseCase = new DefaultAnalysisUseCase(
                new DefaultAnalyseCalculationService(),
                retirementStore,
                taxStore,
                treasuryStore,
                appSettings,
                appSettings,
                treasuryStore,
                patrimoineAdapter,
                bankStore);

        service = new AnalyseServiceImpl(mapper,
                new ObjectifsSettingsService(new InMemoryObjectifsSettingsStore()),
                analysisUseCase,
                retirementStore,
                taxStore,
                treasuryStore,
                appSettings,
                appSettings,
                treasuryStore,
                patrimoineAdapter,
                retirementStore,
                taxStore,
                new InMemoryLoanStore(),
                bankStore,
                new InMemoryGoalStore());
    }

    @Test
    @DisplayName("getAnalyse() doit retourner 200 OK avec le DTO d'analyse complet")
    void testGetAnalyse() {
        ResponseEntity<AnalyseResponseDto> response = service.getAnalyse(12);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        AnalyseResponseDto body = response.getBody();
        assertNotNull(body.getData());
        assertNotNull(body.getBankImport());
        assertNotNull(body.getCharges());
        assertNotNull(body.getIncomes());
        assertNotNull(body.getPlacements());
        assertNotNull(body.getSettings());
        assertNotNull(body.getKpis());
        assertNotNull(body.getLandingData());
        assertNotNull(body.getDriftRows());
        assertNotNull(body.getMonthlyCompareData());
        assertNotNull(body.getCategorySummaries());
        assertNotNull(body.getCurrentMonthISO());
        assertNotNull(body.getCurrentMonthLabel());
    }
}
