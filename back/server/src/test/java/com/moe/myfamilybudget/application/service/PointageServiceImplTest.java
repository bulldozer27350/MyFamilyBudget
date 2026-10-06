package com.moe.myfamilybudget.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.moe.myfamilybudget.domain.bankpointage.core.DefaultPointageCalculationService;
import com.moe.myfamilybudget.application.mapper.PointageMapper;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.application.command.BankImportCommandService;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryBankStore;
import com.moe.myfamilybudget.server.internal.testsupport.RecordingTransactionRunner;
import com.moe.myfamilybudget.persistence.adapter.BudgetPersistenceAdapter;
import com.moe.myfamilybudget.persistence.adapter.PatrimoinePersistenceAdapter;
import com.moe.myfamilybudget.server.internal.testsupport.SettingsReaderTestFactory;
import com.moe.myfamilybudget.server.internal.testsupport.PersistenceManagerTestFactory;

@DisplayName("PointageServiceImpl OpenAPI Controller Unit Tests")
class PointageServiceImplTest {

    private final InMemoryBankStore bankStore = new InMemoryBankStore();

    private PointageServiceImpl service;
    private PersistenceManager persistenceManager;

    @BeforeEach
    void setUp() {
        persistenceManager = PersistenceManagerTestFactory.inMemory();
        persistenceManager.init();
        service = new PointageServiceImpl(
                bankStore, new BudgetPersistenceAdapter(persistenceManager),
                new PatrimoinePersistenceAdapter(persistenceManager), SettingsReaderTestFactory.of(persistenceManager),
                new BankImportCommandService(bankStore, silos -> { }, RecordingTransactionRunner.direct()), new PointageMapper(),
                new DefaultPointageCalculationService());
    }

    @Test
    @DisplayName("getPointage returns 200 OK with pointage response payload map")
    void testGetPointage() {
        ResponseEntity<Object> response = service.getPointage();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsKeys("transactions", "categories", "matchings", "charges", "incomes", "placements", "settings");
    }

    @Test
    @DisplayName("savePointageMatching updates pointage links for month and returns 200 OK")
    void testSavePointageMatching() {
        List<Map<String, Object>> newLinksBody = List.of(
                Map.of("budgetLineId", "c1", "txIds", List.of("tx101", "tx102"))
        );

        ResponseEntity<Void> response = service.savePointageMatching("2026-05", newLinksBody);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        BankImportModel bankImport = bankStore.getBankImport();
        assertThat(bankImport.matchings()).isNotEmpty();
        BankImportModel.MatchingModel monthMatching = bankImport.matchings().stream()
                .filter(m -> "2026-05".equals(m.month()))
                .findFirst()
                .orElse(null);

        assertThat(monthMatching).isNotNull();
        assertThat(monthMatching.links()).hasSize(1);
        assertThat(monthMatching.links().get(0).budgetLineId()).isEqualTo("c1");
        assertThat(monthMatching.links().get(0).txIds()).containsExactly("tx101", "tx102");
    }

    @Test
    @DisplayName("savePointageMatching throws exception if monthISO is blank")
    void testSavePointageMatchingInvalidMonth() {
        assertThatThrownBy(() -> service.savePointageMatching("", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
