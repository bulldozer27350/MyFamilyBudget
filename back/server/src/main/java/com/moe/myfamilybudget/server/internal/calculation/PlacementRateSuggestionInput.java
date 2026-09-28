package com.moe.myfamilybudget.server.internal.calculation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.moe.myfamilybudget.server.internal.marketdata.MarketRatesView;

/**
 * Contrat d'entrée du moteur de suggestions de taux (RF-802, voir
 * doc/architecture/10-domaine-prets-suggestions.md).
 *
 * <p>Le moteur ne connaît ni {@code PlacementModel} ni {@code AssetCategoryModel} : la résolution
 * du bucket d'actif de chaque placement est faite par l'appelant ({@code
 * PlacementRateSuggestionInputFactory}). Le calcul lui-même n'est pas modifié.
 *
 * @param placements placements à évaluer, bucket déjà résolu
 * @param market     données de marché courantes (Marché → Suggestions), {@code null} si indisponibles
 * @param amplitude  écart pessimiste/optimiste en fraction, {@code null} pour la valeur par défaut ;
 *                   validé par le moteur (entre 0 et 0,05)
 * @param today      date de référence (injectée pour des calculs reproductibles)
 */
public record PlacementRateSuggestionInput(
        List<PlacementRateInput> placements,
        MarketRatesView market,
        BigDecimal amplitude,
        LocalDate today) {

    public PlacementRateSuggestionInput {
        if (placements == null) placements = Collections.emptyList();
        Objects.requireNonNull(today, "today");
    }

    /**
     * Projection minimale d'un placement pour les suggestions de taux.
     *
     * @param id       identifiant du placement
     * @param label    libellé (sert à repérer Livret A, LDDS, LEP)
     * @param category nom de la catégorie d'actif saisie (sert aussi à repérer les livrets réglementés)
     * @param bucket   bucket d'actif résolu (ex. {@code "cash"}, {@code "fondsEuros"}), {@code null} si inconnu
     * @param ratePess taux pessimiste actuellement saisi
     * @param rateCorr taux correct actuellement saisi
     * @param rateOpti taux optimiste actuellement saisi
     */
    public record PlacementRateInput(
            String id,
            String label,
            String category,
            String bucket,
            BigDecimal ratePess,
            BigDecimal rateCorr,
            BigDecimal rateOpti) {
    }
}
