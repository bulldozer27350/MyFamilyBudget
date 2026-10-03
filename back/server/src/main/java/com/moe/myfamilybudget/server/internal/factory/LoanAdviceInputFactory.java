package com.moe.myfamilybudget.server.internal.factory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.moe.myfamilybudget.server.internal.calculation.LiquidPlacementAlternative;
import com.moe.myfamilybudget.server.internal.calculation.LoanAdviceInput;
import com.moe.myfamilybudget.server.internal.calculation.LoanAdviceParameters;
import com.moe.myfamilybudget.server.internal.calculation.LoanInput;
import com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.LoanModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;

/**
 * Construit un {@link LoanAdviceInput} (RF-801, voir doc/architecture/10-domaine-prets-suggestions.md).
 *
 * <p>C'est ici, et non plus dans le moteur d'analyse des prêts, que {@code LoanModel},
 * {@code PlacementModel} et {@code AssetCategoryModel} sont traduits en {@link LoanInput} et
 * {@link LiquidPlacementAlternative} : le bucket d'actif de chaque placement est résolu par
 * l'assemblage. Encapsulation de frontière uniquement, aucune règle de calcul n'est déplacée.
 *
 * <p>Cette classe vit dans {@code internal.factory}, hors du package {@code internal.calculation}
 * gardé par ArchUnit.
 */
public final class LoanAdviceInputFactory {

    private LoanAdviceInputFactory() {
    }

    /**
     * Assemble l'entrée depuis le budget courant.
     *
     * @param data       budget courant (prêts, placements, catégories d'actifs)
     * @param parameters hypothèses de l'analyse
     * @param marketRate taux de marché résolu par l'appelant, {@code null} si inconnu
     * @param today      date de référence des projections
     */
    public static LoanAdviceInput from(BudgetDataModel data, LoanAdviceParameters parameters,
            BigDecimal marketRate, LocalDate today) {
        Objects.requireNonNull(data, "data");
        return from(data.getEffectiveLoans(), data.getEffectivePlacements(), data.getEffectiveAssetCategories(),
                parameters, marketRate, today);
    }

    /** Assemble l'entrée depuis les sous-modèles déjà extraits ; les listes peuvent être {@code null}. */
    public static LoanAdviceInput from(List<LoanModel> loans, List<PlacementModel> placements,
            List<AssetCategoryModel> categories, LoanAdviceParameters parameters, BigDecimal marketRate,
            LocalDate today) {
        List<LoanInput> loanInputs = new ArrayList<>();
        for (LoanModel loan : loans == null ? List.<LoanModel>of() : loans) {
            loanInputs.add(toLoanInput(loan));
        }

        AssetBucketResolver buckets = new AssetBucketResolver(categories);
        List<LiquidPlacementAlternative> alternatives = new ArrayList<>();
        for (PlacementModel placement : placements == null ? List.<PlacementModel>of() : placements) {
            alternatives.add(new LiquidPlacementAlternative(
                    placement.label(), placement.rateCorr(), buckets.bucketOf(placement)));
        }
        return new LoanAdviceInput(loanInputs, alternatives, parameters, marketRate, today);
    }

    /** Ne retient que les champs consommés par le calcul ({@code initialAmount} et {@code totalInstallments} sont écartés). */
    public static LoanInput toLoanInput(LoanModel loan) {
        return new LoanInput(loan.id(), loan.label(), loan.crd(), loan.rate(), loan.monthly(), loan.insurance(),
                loan.startDate(), loan.endDate(), loan.stepDate());
    }
}
