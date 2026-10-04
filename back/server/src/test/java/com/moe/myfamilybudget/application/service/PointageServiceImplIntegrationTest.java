package com.moe.myfamilybudget.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.moe.myfamilybudget.application.mapper.PointageMapper;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.application.command.BankImportCommandService;
import com.moe.myfamilybudget.persistence.adapter.BankPersistenceAdapter;
import com.moe.myfamilybudget.persistence.adapter.BudgetPersistenceAdapter;
import com.moe.myfamilybudget.persistence.adapter.PatrimoinePersistenceAdapter;
import com.moe.myfamilybudget.server.internal.testsupport.SettingsReaderTestFactory;
import com.moe.myfamilybudget.server.internal.testsupport.PersistenceManagerTestFactory;

@DisplayName("PointageServiceImpl Integration Test")
class PointageServiceImplIntegrationTest {

    private PointageServiceImpl service;
    private PersistenceManager persistenceManager;

    @BeforeEach
    void setUp() {
        persistenceManager = PersistenceManagerTestFactory.inMemory();
        persistenceManager.init();
        service = new PointageServiceImpl(
                new BankPersistenceAdapter(persistenceManager), new BudgetPersistenceAdapter(persistenceManager),
                new PatrimoinePersistenceAdapter(persistenceManager), SettingsReaderTestFactory.of(persistenceManager),
                new BankImportCommandService(new BankPersistenceAdapter(persistenceManager)), new PointageMapper());
    }

    @Test
    @DisplayName("End-to-end flow: Save pointage matching and retrieve via getPointage")
    void testEndToEndPointageFlow() {
        // 1. Initial GET
        ResponseEntity<Object> initialResp = service.getPointage();
        assertThat(initialResp.getStatusCode().is2xxSuccessful()).isTrue();

        // 2. Save matching for 2026-06
        List<Map<String, Object>> links = List.of(
                Map.of("budgetLineId", "c_loyer", "txIds", List.of("tx_loyer_01"))
        );
        ResponseEntity<Void> saveResp = service.savePointageMatching("2026-06", links);
        assertThat(saveResp.getStatusCode().is2xxSuccessful()).isTrue();

        // 3. Verify via GET
        ResponseEntity<Object> afterResp = service.getPointage();
        assertThat(afterResp.getStatusCode().is2xxSuccessful()).isTrue();

        BankImportModel bankImport = persistenceManager.getBankImport();
        assertThat(bankImport.matchings()).anyMatch(m -> "2026-06".equals(m.month()));
    }
}
