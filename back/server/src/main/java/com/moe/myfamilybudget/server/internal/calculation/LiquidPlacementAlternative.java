package com.moe.myfamilybudget.server.internal.calculation;

import java.math.BigDecimal;

/**
 * Placement candidat comme alternative au remboursement anticipé d'un prêt (RF-800, voir
 * doc/architecture/10-domaine-prets-suggestions.md).
 *
 * <p>La résolution du bucket d'actif — aujourd'hui faite à partir de {@code PlacementModel} et
 * {@code AssetCategoryModel} — incombe à l'appelant : le moteur reçoit un bucket déjà résolu et
 * ne connaît ni les placements ni les catégories d'actifs.
 *
 * @param label    libellé du placement
 * @param rateCorr taux de rendement « correct » (fraction), {@code null} si inconnu
 * @param bucket   bucket d'actif résolu (ex. {@code "cash"}, {@code "fondsEuros"}) ; le moteur ne
 *                 retient que les buckets liquides et sans risque
 */
public record LiquidPlacementAlternative(String label, BigDecimal rateCorr, String bucket) {

    public LiquidPlacementAlternative {
        if (label == null) label = "";
        if (bucket == null) bucket = "";
    }
}
