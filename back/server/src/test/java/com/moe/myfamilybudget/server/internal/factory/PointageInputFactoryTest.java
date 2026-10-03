package com.moe.myfamilybudget.server.internal.factory;

import com.moe.myfamilybudget.application.factory.PointageInputFactory;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.domain.bankpointage.calculation.BudgetLineProjection;
import com.moe.myfamilybudget.domain.bankpointage.calculation.PointageInput;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.transition.model.BudgetDataModel;
import com.moe.myfamilybudget.domain.budget.ChargeModel;
import com.moe.myfamilybudget.domain.budget.IncomeModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;

/**
 * RF-501 : vérifie la composition des lignes budgétaires actives et l'assemblage de
 * {@link PointageInput} réalisés par {@link PointageInputFactory} (ex-logique de
 * {@code PointageCalculator.calculateActiveBudgetLines}, reprise à l'identique).
 */
class PointageInputFactoryTest {

    private static final ChargeModel LOYER =
            new ChargeModel("c1", "Loyer", new BigDecimal("800"), "2026-01", "2026-12", null, "cat1", "");
    private static final ChargeModel ASSURANCE =
            new ChargeModel("c2", "Assurance", new BigDecimal("50"), "2027-01", null, null, "cat1", "");
    private static final IncomeModel SALAIRE =
            new IncomeModel("i1", "Salaire", new BigDecimal("3500"), "2026-01", null, null, "cat2", "");
    private static final PlacementModel LIVRET =
            new PlacementModel("p1", "Livret A", "cat3", new BigDecimal("1000"), "2026-01", new BigDecimal("200"),
                    "2026-01", "2026-12", null, null, null, null, "");
    private static final PlacementModel PLACEMENT_SANS_VERSEMENT =
            new PlacementModel("p2", "PEA", "cat4", new BigDecimal("500"), "2026-01", BigDecimal.ZERO,
                    "2026-01", "2026-12", null, null, null, null, "");

    @Test
    @DisplayName("activeBudgetLines filtre charges, revenus et placements actifs pour le mois")
    void testActiveBudgetLines() {
        List<BudgetLineProjection> lines = PointageInputFactory.activeBudgetLines(
                List.of(LOYER, ASSURANCE), List.of(SALAIRE), List.of(LIVRET, PLACEMENT_SANS_VERSEMENT), null, "2026-05");

        // Assurance pas encore démarrée, placement sans versement mensuel ignoré.
        assertThat(lines).extracting(BudgetLineProjection::id).containsExactly("c1", "i1", "p1");
        assertThat(lines.get(0).monthly()).isEqualByComparingTo("800.00");
        assertThat(lines.get(0).kind()).isEqualTo("charge");
        assertThat(lines.get(1).kind()).isEqualTo("revenu");
        assertThat(lines.get(2).kind()).isEqualTo("placement");
        assertThat(lines.get(2).label()).isEqualTo("Épargne : Livret A");
        assertThat(lines.get(2).categoryId()).isEqualTo("cat3");
    }

    @Test
    @DisplayName("activeBudgetLines exclut les lignes hors de leur période de validité")
    void testActiveBudgetLinesOutsidePeriod() {
        List<BudgetLineProjection> lines = PointageInputFactory.activeBudgetLines(
                List.of(LOYER, ASSURANCE), List.of(SALAIRE), List.of(LIVRET), null, "2027-03");

        // Loyer terminé fin 2026, livret terminé fin 2026 ; l'assurance démarre en 2027.
        assertThat(lines).extracting(BudgetLineProjection::id).containsExactly("c2", "i1");
    }

    @Test
    @DisplayName("activeBudgetLines applique la croissance annuelle (inflation par défaut pour une charge)")
    void testActiveBudgetLinesGrowth() {
        ChargeModel chargeSansFin = new ChargeModel("c3", "Abonnement", new BigDecimal("100"), "2026-01", null,
                new BigDecimal("0.10"), "cat1", "");

        List<BudgetLineProjection> lines = PointageInputFactory.activeBudgetLines(
                List.of(chargeSansFin), List.of(), List.of(), null, "2028-01");

        // 100 * 1.10^2 = 121.00
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).monthly()).isEqualByComparingTo("121.00");
    }

    @Test
    @DisplayName("activeBudgetLines renvoie une liste vide si le mois est absent ou si les listes sont nulles")
    void testActiveBudgetLinesEmptyCases() {
        assertThat(PointageInputFactory.activeBudgetLines(List.of(LOYER), List.of(), List.of(), null, null)).isEmpty();
        assertThat(PointageInputFactory.activeBudgetLines(List.of(LOYER), List.of(), List.of(), null, " ")).isEmpty();
        assertThat(PointageInputFactory.activeBudgetLines(null, null, null, null, "2026-05")).isEmpty();
    }

    @Test
    @DisplayName("from assemble transactions, liens du mois, lignes actives et période")
    void testFrom() {
        BankImportModel.BankTransactionModel tx =
                new BankImportModel.BankTransactionModel("tx1", "2026-05-01", "Loyer mai", new BigDecimal("-800.00"));
        BankImportModel.MatchingLinkModel mai = new BankImportModel.MatchingLinkModel("c1", List.of("tx1"));
        BankImportModel.MatchingLinkModel juin = new BankImportModel.MatchingLinkModel("c1", List.of("tx9"));
        BankImportModel bankImport = new BankImportModel(null, null, null, List.of(tx), null,
                List.of(new BankImportModel.MatchingModel("2026-05", List.of(mai)),
                        new BankImportModel.MatchingModel("2026-06", List.of(juin))));

        BudgetDataModel data = new BudgetDataModel(null, List.of(SALAIRE), List.of(LOYER), List.of(LIVRET),
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        PointageInput input = PointageInputFactory.from(bankImport, data, "2026-05");

        assertThat(input.period().monthISO()).isEqualTo("2026-05");
        assertThat(input.transactions()).containsExactly(tx);
        assertThat(input.matchings()).containsExactly(mai);
        assertThat(input.activeBudgetLines()).extracting(BudgetLineProjection::id).containsExactly("c1", "i1", "p1");
    }

    @Test
    @DisplayName("from renvoie aucun lien si le mois n'a pas de rapprochement")
    void testFromWithoutMatchingForMonth() {
        BankImportModel bankImport = new BankImportModel(null, null, null, null, null, null);
        BudgetDataModel data = new BudgetDataModel(null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        PointageInput input = PointageInputFactory.from(bankImport, data, "2026-05");

        assertThat(input.matchings()).isEmpty();
        assertThat(input.transactions()).isEmpty();
        assertThat(input.activeBudgetLines()).isEmpty();
    }
}
