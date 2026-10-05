package com.moe.myfamilybudget.domain.wealth.calculation;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

import com.moe.myfamilybudget.domain.wealth.model.PatrimoinePerPlacementModel;
import com.moe.myfamilybudget.domain.wealth.model.PatrimoineProjectionsModel;
import com.moe.myfamilybudget.domain.wealth.model.PatrimoineYearModel;
import com.moe.myfamilybudget.domain.wealth.model.ScenarioAmountsModel;

/**
 * Projection patrimoniale pour l'aperçu financier (RF-900, voir
 * doc/architecture/11-domaine-overview.md et 05-domaine-patrimoine.md).
 *
 * <p>Assemble les projections de placements issues de {@link PatrimoineProjectionService},
 * le total actuel des placements et les libellés des placements exclus de la retraite
 * (utilisés pour isoler le patrimoine financier mobilisable à la retraite).
 *
 * @param projections             projections détaillées par placement et totaux annuels
 * @param currentTotalBalance     solde total actuel des placements financiers
 * @param excludedPlacementLabels libellés des placements marqués comme exclus de la retraite
 */
public record PatrimoineProjection(
        PatrimoineProjectionsModel projections,
        BigDecimal currentTotalBalance,
        Set<String> excludedPlacementLabels) {

    public PatrimoineProjection {
        Objects.requireNonNull(projections, "projections");
        currentTotalBalance = currentTotalBalance != null ? currentTotalBalance : BigDecimal.ZERO;
        excludedPlacementLabels = excludedPlacementLabels != null ? Set.copyOf(excludedPlacementLabels) : Set.of();
    }

    /**
     * Calcule le patrimoine financier à un index d'année donné (ex. année de retraite), en excluant
     * les placements non mobilisables pour la retraite, et en appliquant le déflateur d'euros constants.
     *
     * @param yearIndex index de l'année dans les projections (doit correspondre à l'index dans {@code years})
     * @param deflator  déflateur d'inflation (1.0 si euros courants)
     * @return montant pessimiste, corrigé et optimiste du patrimoine financier retraite
     */
    public ScenarioAmountsModel financialOnlyPatrimoine(int yearIndex, BigDecimal deflator) {
        if (yearIndex < 0) {
            return new ScenarioAmountsModel(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        BigDecimal pess = BigDecimal.ZERO;
        BigDecimal corr = BigDecimal.ZERO;
        BigDecimal opti = BigDecimal.ZERO;

        for (PatrimoinePerPlacementModel pp : projections.perPlacement()) {
            if (excludedPlacementLabels.contains(pp.label())) {
                continue;
            }
            if (yearIndex < pp.rows().size()) {
                PatrimoineYearModel row = pp.rows().get(yearIndex);
                pess = pess.add(row.pess());
                corr = corr.add(row.corr());
                opti = opti.add(row.opti());
            }
        }

        BigDecimal d = deflator != null ? deflator : BigDecimal.ONE;
        return new ScenarioAmountsModel(
                pess.multiply(d),
                corr.multiply(d),
                opti.multiply(d));
    }
}
