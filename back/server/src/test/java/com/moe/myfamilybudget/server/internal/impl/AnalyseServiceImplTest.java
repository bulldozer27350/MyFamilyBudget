package com.moe.myfamilybudget.server.internal.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.moe.myfamilybudget.api.model.AnalyseResponseDto;
import com.moe.myfamilybudget.server.internal.mapper.AnalyseMapper;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;
import com.moe.myfamilybudget.application.settings.ObjectifsSettingsService;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryObjectifsSettingsStore;
import com.moe.myfamilybudget.server.internal.persistence.adapter.BankPersistenceAdapter;
import com.moe.myfamilybudget.server.internal.persistence.adapter.BudgetPersistenceAdapter;
import com.moe.myfamilybudget.server.internal.persistence.adapter.GoalPersistenceAdapter;
import com.moe.myfamilybudget.server.internal.persistence.adapter.LoanPersistenceAdapter;
import com.moe.myfamilybudget.server.internal.persistence.adapter.PatrimoinePersistenceAdapter;
import com.moe.myfamilybudget.server.internal.persistence.adapter.RetirementPersistenceAdapter;
import com.moe.myfamilybudget.server.internal.persistence.adapter.SettingsPersistenceAdapter;
import com.moe.myfamilybudget.server.internal.persistence.adapter.TaxPersistenceAdapter;
import com.moe.myfamilybudget.server.internal.testsupport.PersistenceManagerTestFactory;

class AnalyseServiceImplTest {

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
                new SettingsPersistenceAdapter(persistenceManager),
                new BudgetPersistenceAdapter(persistenceManager),
                new PatrimoinePersistenceAdapter(persistenceManager),
                new RetirementPersistenceAdapter(persistenceManager),
                new TaxPersistenceAdapter(persistenceManager),
                new LoanPersistenceAdapter(persistenceManager),
                new BankPersistenceAdapter(persistenceManager),
                new GoalPersistenceAdapter(persistenceManager));
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
