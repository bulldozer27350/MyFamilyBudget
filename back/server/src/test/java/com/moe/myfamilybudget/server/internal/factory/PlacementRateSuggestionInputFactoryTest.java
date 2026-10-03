package com.moe.myfamilybudget.server.internal.factory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.server.internal.calculation.PlacementRateSuggestionInput;
import com.moe.myfamilybudget.server.internal.calculation.PlacementRateSuggestionInput.PlacementRateInput;
import com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;

class PlacementRateSuggestionInputFactoryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);
    private static final List<AssetCategoryModel> CATEGORIES = List.of(
            new AssetCategoryModel("c1", "💶", "Livrets", "cash"),
            new AssetCategoryModel("c2", "📈", "PEA", "actions"));

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static PlacementModel placement(String id, String label, String category) {
        return new PlacementModel(id, label, category, bd("10000"), "2026-09-01", bd("0"), null, null,
                bd("0.011"), bd("0.022"), bd("0.033"), false, "");
    }

    @Test
    @DisplayName("Le placement est projeté avec son bucket résolu et ses taux actuels")
    void projectsPlacement() {
        PlacementRateSuggestionInput input = PlacementRateSuggestionInputFactory.from(
                List.of(placement("p1", "Livret A", "Livrets")), CATEGORIES, null, bd("0.02"), TODAY);

        assertThat(input.placements()).containsExactly(new PlacementRateInput(
                "p1", "Livret A", "Livrets", "cash", bd("0.011"), bd("0.022"), bd("0.033")));
        assertThat(input.amplitude()).isEqualByComparingTo("0.02");
        assertThat(input.market()).isNull();
        assertThat(input.today()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("Catégorie inconnue : bucket null ; listes nulles : entrée vide")
    void unknownCategoryAndNullLists() {
        PlacementRateSuggestionInput unknown = PlacementRateSuggestionInputFactory.from(
                List.of(placement("p2", "Truc", "Inconnue")), CATEGORIES, null, null, TODAY);
        assertThat(unknown.placements().get(0).bucket()).isNull();

        PlacementRateSuggestionInput empty = PlacementRateSuggestionInputFactory.from(
                null, null, null, null, TODAY);
        assertThat(empty.placements()).isEmpty();
    }
}
