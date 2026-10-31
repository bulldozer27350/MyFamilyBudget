package com.moe.myfamilybudget.server.internal.factory;

import com.moe.myfamilybudget.application.factory.LoanAdviceInputFactory;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.domain.credit.calculation.LiquidPlacementAlternative;
import com.moe.myfamilybudget.domain.credit.calculation.LoanAdviceCalculationService;
import com.moe.myfamilybudget.domain.credit.core.DefaultLoanAdviceCalculationService;
import com.moe.myfamilybudget.domain.credit.calculation.LoanAdviceInput;
import com.moe.myfamilybudget.domain.credit.calculation.LoanAdviceParameters;
import com.moe.myfamilybudget.domain.credit.calculation.LoanInput;
import com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel;
import com.moe.myfamilybudget.domain.credit.model.LoanAdviceResultModel;
import com.moe.myfamilybudget.domain.credit.model.LoanModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;

class LoanAdviceInputFactoryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static LoanModel loan() {
        return new LoanModel("l1", "Résidence", bd("250000"), bd("0.045"), bd("1450"), bd("50"),
                "2026-09-01", "2046-09-01", bd("300000"), 240, "2036-01-05");
    }

    private static PlacementModel placement(String label, String category, String rateCorr) {
        return new PlacementModel("p-" + label, label, category, bd("10000"), "2026-09-01", bd("0"), null, null,
                bd("0.01"), rateCorr == null ? null : bd(rateCorr), bd("0.05"), false, "");
    }

    @Test
    @DisplayName("toLoanInput() reprend les champs consommés par le calcul, sans initialAmount ni totalInstallments")
    void testLoanMapping() {
        LoanInput input = LoanAdviceInputFactory.toLoanInput(loan());

        assertThat(input).isEqualTo(new LoanInput("l1", "Résidence", bd("250000"), bd("0.045"), bd("1450"),
                bd("50"), "2026-09-01", "2046-09-01", "2036-01-05"));
    }

    @Test
    @DisplayName("Le bucket d'actif est résolu par la factory : par identifiant de catégorie, à défaut par nom")
    void testBucketResolvedByCaller() {
        List<AssetCategoryModel> categories = List.of(
                new AssetCategoryModel("c1", "💶", "Livrets", "cash"),
                new AssetCategoryModel("c3", "📈", "PEA", "actions"));

        LoanAdviceInput input = LoanAdviceInputFactory.from(List.of(loan()),
                List.of(placement("Livret A", "Livrets", "0.02"), placement("PEA", "PEA", "0.08"),
                        placement("Inconnu", "Autre", null)),
                categories, LoanAdviceParameters.defaults(null), null, TODAY);

        assertThat(input.loans()).hasSize(1);
        assertThat(input.alternatives()).extracting(LiquidPlacementAlternative::label, LiquidPlacementAlternative::bucket)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple("Livret A", "cash"),
                        org.assertj.core.api.Assertions.tuple("PEA", "actions"),
                        org.assertj.core.api.Assertions.tuple("Inconnu", ""));
        assertThat(input.today()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("Des listes nulles donnent une entrée vide, sans exception")
    void testNullLists() {
        LoanAdviceInput input = LoanAdviceInputFactory.from(null, null, null, null, null, TODAY);

        assertThat(input.loans()).isEmpty();
        assertThat(input.alternatives()).isEmpty();
    }

    @Test
    @DisplayName("Le taux de marché de l'entrée prime sur celui des hypothèses")
    void testMarketRateOfInputWins() {
        LoanAdviceCalculationService service = new DefaultLoanAdviceCalculationService();
        LoanAdviceInput input = LoanAdviceInputFactory.from(List.of(loan()), List.of(), List.of(),
                LoanAdviceParameters.defaults(bd("0.05")), bd("0.03"), TODAY);

        LoanAdviceResultModel result = service.compute(input);

        assertThat(result.marketRateUsed()).isEqualByComparingTo(bd("0.03"));
    }
}
