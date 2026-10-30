package com.moe.myfamilybudget.web.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.moe.myfamilybudget.domain.bankpointage.core.DefaultBankImportCalculationService;
import com.moe.myfamilybudget.domain.bankpointage.core.DefaultPointageCalculationService;
import com.moe.myfamilybudget.application.mapper.PointageMapper;
import com.moe.myfamilybudget.application.mapper.StatementBankImportMapper;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.application.command.BankImportCommandService;
import com.moe.myfamilybudget.application.usecase.DefaultBankPointageUseCase;
import com.moe.myfamilybudget.application.usecase.bankpointage.BankPointageUseCase;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryBankStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryTreasuryStore;
import com.moe.myfamilybudget.server.internal.testsupport.RecordingTransactionRunner;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryWealthStore;
import com.moe.myfamilybudget.server.internal.adapter.PatrimoinePersistenceAdapter;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryRetirementStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryTaxStore;
import com.moe.myfamilybudget.server.internal.testsupport.CacheBackedAppSettings;

@DisplayName("PointageServiceImpl Integration Test")
class PointageServiceImplIntegrationTest {

    private final InMemoryBankStore bankStore = new InMemoryBankStore();

    private PointageServiceImpl service;

    @BeforeEach
    void setUp() {
        // R-83 : la composition de la réponse et l'écriture sont désormais portées par le cas d'usage
        // BankPointageUseCase (implémenté par DefaultBankPointageUseCase) ; ce test ne vérifie que la
        // traduction HTTP du contrôleur.
        CacheBackedAppSettings appSettings = new CacheBackedAppSettings();
        InMemoryTreasuryStore treasuryStore = new InMemoryTreasuryStore();
        BankPointageUseCase bankPointageUseCase = new DefaultBankPointageUseCase(
                bankStore, treasuryStore,
                new PatrimoinePersistenceAdapter(new InMemoryWealthStore().getDelegate(), treasuryStore.getDelegate()),
                new InMemoryRetirementStore(), new InMemoryTaxStore(), treasuryStore, appSettings, appSettings,
                new BankImportCommandService(bankStore, bankStore, silos -> { }, RecordingTransactionRunner.direct()),
                new DefaultBankImportCalculationService(), new DefaultPointageCalculationService(),
                new StatementBankImportMapper(), new PointageMapper());
        service = new PointageServiceImpl(bankPointageUseCase);
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

        BankImportModel bankImport = bankStore.getBankImport();
        assertThat(bankImport.matchings()).anyMatch(m -> "2026-06".equals(m.month()));
    }
}
