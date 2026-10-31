package com.moe.myfamilybudget.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.application.command.PatrimoineCommandService;
import com.moe.myfamilybudget.application.usecase.wealth.WealthResultModel;
import com.moe.myfamilybudget.application.usecase.wealth.WealthUseCase;
import com.moe.myfamilybudget.domain.wealth.core.DefaultPatrimoineProjectionService;
import com.moe.myfamilybudget.domain.wealth.core.DefaultPlacementEvolutionService;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.adapter.PatrimoinePersistenceAdapter;
import com.moe.myfamilybudget.server.internal.testsupport.CacheBackedAppSettings;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryBankStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryLoanStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryRetirementStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryTreasuryStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryWealthStore;
import com.moe.myfamilybudget.server.internal.testsupport.RecordingTransactionRunner;

/**
 * R-81 (meme gabarit que {@code DefaultTaxUseCaseTest}, SILO-312) : le cas d'usage compose la reponse
 * Patrimoine sans {@code ResponseEntity} ni DTO, et traduit les types des silos (y compris Credit et
 * Banque/Pointage, lus en inter-silo) vers les modeles de {@code application-api}.
 */
class DefaultWealthUseCaseTest {

    private final InMemoryBankStore bankStore = new InMemoryBankStore();

    private WealthUseCase useCase;
    private InMemoryWealthStore wealthStore;
    private InMemoryTreasuryStore treasuryStore;
    private PatrimoinePersistenceAdapter patrimoineAdapter;

    @BeforeEach
    void setUp() {
        wealthStore = new InMemoryWealthStore();
        treasuryStore = new InMemoryTreasuryStore();
        InMemoryRetirementStore retirementStore = new InMemoryRetirementStore();
        CacheBackedAppSettings appSettings = new CacheBackedAppSettings();
        InMemoryLoanStore loanStore = new InMemoryLoanStore();
        patrimoineAdapter = new PatrimoinePersistenceAdapter(wealthStore.getDelegate(), treasuryStore.getDelegate());
        PatrimoineCommandService patrimoineCommandService = new PatrimoineCommandService(
                patrimoineAdapter, silos -> { }, RecordingTransactionRunner.direct());

        useCase = new DefaultWealthUseCase(
                new DefaultPatrimoineProjectionService(),
                new DefaultPlacementEvolutionService(),
                patrimoineCommandService,
                retirementStore,
                treasuryStore,
                appSettings,
                patrimoineAdapter,
                treasuryStore,
                loanStore,
                bankStore);
    }

    @Test
    @DisplayName("getPatrimoine() renvoie un modele complet : placements, virements, immobilier, categories, projections")
    void returnsACompleteResult() {
        WealthResultModel result = useCase.getPatrimoine(false);

        assertThat(result).isNotNull();
        assertThat(result.placements()).isNotNull();
        assertThat(result.transfers()).isNotNull();
        assertThat(result.realEstate()).isNotNull();
        assertThat(result.loans()).isNotNull();
        assertThat(result.assetCategories()).isNotNull();
        assertThat(result.bankCategories()).isNotNull();
        assertThat(result.projections()).isNotNull();
        assertThat(result.projections().totals()).isNotEmpty();
    }

    @Test
    @DisplayName("savePatrimoineRow() ecrit la ligne puis getPatrimoine() la relit")
    void savesARowAndRereadsIt() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", "plc_uc1");
        row.put("label", "Mon PEA");
        row.put("balance", new BigDecimal("10000"));

        useCase.savePatrimoineRow("placements", row);

        WealthResultModel result = useCase.getPatrimoine(false);
        assertThat(result.placements()).anySatisfy(p -> {
            assertThat(p.id()).isEqualTo("plc_uc1");
            assertThat(p.label()).isEqualTo("Mon PEA");
        });
    }

    @Test
    @DisplayName("deletePatrimoineRow() supprime la ligne")
    void deletesARow() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", "plc_uc2");
        row.put("label", "A supprimer");
        useCase.savePatrimoineRow("placements", row);

        useCase.deletePatrimoineRow("placements", "plc_uc2");

        WealthResultModel result = useCase.getPatrimoine(false);
        assertThat(result.placements()).noneMatch(p -> "plc_uc2".equals(p.id()));
    }

    @Test
    @DisplayName("getPlacementEvolution() renvoie null pour un placement inconnu")
    void returnsNullForAnUnknownPlacement() {
        assertThat(useCase.getPlacementEvolution("placement_inconnu", false)).isNull();
    }

    @Test
    @DisplayName("getPlacementEvolution() renvoie la chronologie d'un placement connu")
    void returnsTheEvolutionOfAKnownPlacement() {
        PlacementModel placement = new PlacementModel(
                "plc_evo", "Mon PEA", "Bourse", new BigDecimal("10000"), "2026-01-01",
                BigDecimal.ZERO, "2026-01-01", null, BigDecimal.ZERO, new BigDecimal("0.05"), BigDecimal.ZERO,
                false, null, null, null, null, null, "cat_bourse");
        wealthStore.replace(List.of(placement), List.of(), List.of(), List.of());

        var evolution = useCase.getPlacementEvolution("plc_evo", false);

        assertThat(evolution).isNotNull();
        assertThat(evolution.placementId()).isEqualTo("plc_evo");
        assertThat(evolution.points()).isNotEmpty();
    }

    @Test
    @DisplayName("addPlacementHistoryEntry() puis deletePlacementHistoryEntry() : aller-retour")
    void addsThenDeletesAHistoryEntry() {
        PlacementModel placement = new PlacementModel(
                "plc_hist", "Mon PEA", "Bourse", new BigDecimal("10000"), "2026-01-01",
                BigDecimal.ZERO, "2026-01-01", null, BigDecimal.ZERO, new BigDecimal("0.05"), BigDecimal.ZERO,
                false, null, null, null, null, null, "cat_bourse");
        wealthStore.replace(List.of(placement), List.of(), List.of(), List.of());

        Map<String, Object> entry = new HashMap<>();
        entry.put("date", "2026-02-01");
        entry.put("value", new BigDecimal("10500"));
        Map<String, Object> saved = useCase.addPlacementHistoryEntry("plc_hist", entry);
        assertThat(saved).containsKey("id");
        String entryId = String.valueOf(saved.get("id"));

        useCase.deletePlacementHistoryEntry("plc_hist", entryId);

        WealthResultModel result = useCase.getPatrimoine(false);
        assertThat(result.placements()).filteredOn(p -> "plc_hist".equals(p.id()))
                .allSatisfy(p -> assertThat(p.history()).noneMatch(h -> entryId.equals(h.id())));
    }
}
