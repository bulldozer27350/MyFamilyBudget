package com.moe.myfamilybudget.application.factory;

import com.moe.myfamilybudget.application.rate.PlacementRateSuggestionInput;
import com.moe.myfamilybudget.application.rate.PlacementRateSuggestionInput.PlacementRateInput;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.moe.myfamilybudget.domain.market.model.MarketRatesView;
import com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;

/**
 * Construit un {@link PlacementRateSuggestionInput} (RF-802, voir
 * doc/architecture/10-domaine-prets-suggestions.md).
 *
 * <p>Déplacé de {@code server.internal.factory} vers {@code application.factory} par R-86
 * (SILO-317) : décision du 14/10/2026, ce cas d'usage transverse Patrimoine+Marché est rattaché à
 * {@code application}.
 *
 * <p>C'est ici, et non plus dans le moteur de suggestions, que {@code PlacementModel} et
 * {@code AssetCategoryModel} sont traduits en {@link PlacementRateInput} : le bucket d'actif de
 * chaque placement est résolu par l'assemblage via {@link AssetBucketResolver}.
 *
 * <p>Cette classe vit dans {@code application.factory}, à côté des factories qui traduisent les
 * placements et catégories d'actifs en Inputs (prêts, suggestions de taux) ; les moteurs de calcul
 * reçoivent un bucket déjà résolu et ne peuvent plus l'utiliser. Il ne doit pas devenir une
 * dépendance commune des domaines.
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
