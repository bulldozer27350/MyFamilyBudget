package com.moe.myfamilybudget.server.internal.factory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.moe.myfamilybudget.server.internal.calculation.PlacementRateSuggestionInput;
import com.moe.myfamilybudget.server.internal.calculation.PlacementRateSuggestionInput.PlacementRateInput;
import com.moe.myfamilybudget.server.internal.marketdata.MarketRatesView;
import com.moe.myfamilybudget.server.internal.model.AssetCategoryModel;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;

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
 */
public final class PlacementRateSuggestionInputFactory {

    private PlacementRateSuggestionInputFactory() {
    }

    /**
     * Assemble l'entrée depuis le budget courant.
     *
     * @param data      budget courant (placements, catégories d'actifs)
     * @param market    données de marché courantes, {@code null} si indisponibles
     * @param amplitude écart pessimiste/optimiste demandé, {@code null} pour la valeur par défaut
     * @param today     date de référence
     */
    public static PlacementRateSuggestionInput from(BudgetDataModel data, MarketRatesView market,
            BigDecimal amplitude, LocalDate today) {
        Objects.requireNonNull(data, "data");
        return from(data.getEffectivePlacements(), data.getEffectiveAssetCategories(), market, amplitude, today);
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
