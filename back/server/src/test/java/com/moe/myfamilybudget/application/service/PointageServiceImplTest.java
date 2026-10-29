package com.moe.myfamilybudget.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

@DisplayName("PointageServiceImpl OpenAPI Controller Unit Tests")
class PointageServiceImplTest {

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
