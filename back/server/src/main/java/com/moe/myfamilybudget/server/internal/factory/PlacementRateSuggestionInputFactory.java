package com.moe.myfamilybudget.server.internal.factory;

import com.moe.myfamilybudget.application.factory.AssetBucketResolver;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.moe.myfamilybudget.server.internal.calculation.PlacementRateSuggestionInput;
import com.moe.myfamilybudget.server.internal.calculation.PlacementRateSuggestionInput.PlacementRateInput;
import com.moe.myfamilybudget.server.internal.marketdata.MarketRatesView;
import com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;

/**
 * Construit un {@link PlacementRateSuggestionInput} (RF-802, voir
 * doc/architecture/10-domaine-prets-suggestions.md).
 *
 * <p>C'est ici, et non plus dans le moteur de suggestions, que {@code PlacementModel} et
 * {@code AssetCategoryModel} sont traduits en {@link PlacementRateInput} : le bucket d'actif de
 * chaque placement est résolu par l'assemblage via {@link AssetBucketResolver}.
 *
 * <p>Cette classe vit dans {@code internal.factory}, hors du package {@code internal.calculation}
 * gardé par ArchUnit.
 *
 * <p>SILO-116 : cette factory ne connaît plus {@code BudgetDataModel} ; l'appelant lui fournit les
 * fragments lus via {@code PatrimoineReader}.
 */
public final class PlacementRateSuggestionInputFactory {

    private PlacementRateSuggestionInputFactory() {
    }

    /** Assemble l'entrée depuis les sous-modèles déjà extraits ; les listes peuvent être {@code null}. */
    public static PlacementRateSuggestionInput from(List<PlacementModel> placements,
            List<AssetCategoryModel> categories, MarketRatesView market, BigDecimal amplitude, LocalDate today) {
        AssetBucketResolver buckets = new AssetBucketResolver(categories);
        List<PlacementRateInput> inputs = new ArrayList<>();
        for (PlacementModel p : placements == null ? List.<PlacementModel>of() : placements) {
            inputs.add(new PlacementRateInput(p.id(), p.label(), p.category(), buckets.bucketOf(p),
                    p.ratePess(), p.rateCorr(), p.rateOpti()));
        }
        return new PlacementRateSuggestionInput(inputs, market, amplitude, today);
    }
}
