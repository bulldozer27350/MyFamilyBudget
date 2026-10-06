package com.moe.myfamilybudget.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.moe.myfamilybudget.api.model.AnalyseResponseDto;
import com.moe.myfamilybudget.application.mapper.AnalyseMapper;
import com.moe.myfamilybudget.domain.analysis.core.DefaultAnalyseCalculationService;
import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.application.settings.ObjectifsSettingsService;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryObjectifsSettingsStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryBankStore;
import com.moe.myfamilybudget.persistence.adapter.BudgetPersistenceAdapter;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryGoalStore;
import com.moe.myfamilybudget.persistence.adapter.LoanPersistenceAdapter;
import com.moe.myfamilybudget.persistence.adapter.PatrimoinePersistenceAdapter;
import com.moe.myfamilybudget.persistence.adapter.RetirementPersistenceAdapter;
import com.moe.myfamilybudget.server.internal.testsupport.SettingsReaderTestFactory;
import com.moe.myfamilybudget.persistence.adapter.TaxPersistenceAdapter;
import com.moe.myfamilybudget.server.internal.testsupport.PersistenceManagerTestFactory;

class AnalyseServiceImplTest {

    private final InMemoryBankStore bankStore = new InMemoryBankStore();

    private AnalyseServiceImpl service;
    private AnalyseMapper mapper;
    private PersistenceManager persistenceManager;

    @BeforeEach
    void setUp() {
        mapper = new AnalyseMapper();
        persistenceManager = PersistenceManagerTestFactory.inMemory();
        persistenceManager.init();
        service = new AnalyseServiceImpl(mapper,
                new ObjectifsSettingsService(new InMemoryObjectifsSettingsStore()),
                SettingsReaderTestFactory.of(persistenceManager),
                new BudgetPersistenceAdapter(persistenceManager),
                new PatrimoinePersistenceAdapter(persistenceManager),
                new RetirementPersistenceAdapter(persistenceManager),
                new TaxPersistenceAdapter(persistenceManager),
                new LoanPersistenceAdapter(persistenceManager),
                bankStore,
                new InMemoryGoalStore(),
                new DefaultAnalyseCalculationService());
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
