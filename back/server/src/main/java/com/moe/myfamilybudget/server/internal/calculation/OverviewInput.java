package com.moe.myfamilybudget.server.internal.calculation;

import java.util.Objects;

import com.moe.myfamilybudget.domain.retirement.model.RetirementProjection;

/**
 * Contrat d'entrée du calcul de l'aperçu financier global (RF-900, voir
 * doc/architecture/11-domaine-overview.md).
 *
 * <p>Overview est un pur agrégateur de projections : il ne dépend d'aucun modèle persistant
 * et reçoit uniquement des projections déjà produites par les domaines respectifs :
 * <ul>
 *   <li>{@link TreasuryProjection} (Trésorerie, RF-401)</li>
 *   <li>{@link PatrimoineProjection} (Patrimoine, RF-301)</li>
 *   <li>{@link RetirementProjection} (Retraite, RF-101)</li>
 *   <li>{@link TaxProjection} (Fiscalité, RF-203 / RF-400)</li>
 *   <li>{@link RealEstateProjection} (Immobilier)</li>
 * </ul>
 *
 * @param treasuryProjection   projections des flux annuels de trésorerie
 * @param patrimoineProjection projections du patrimoine financier
 * @param retirementProjection projections des pensions de retraite
 * @param taxProjection        projections fiscales
 * @param realEstateProjection projections de la valeur immobilière à la retraite
 * @param parameters           paramètres d'horizon, inflation, solde pivot et devises constantes
 */
public record OverviewInput(
        TreasuryProjection treasuryProjection,
        PatrimoineProjection patrimoineProjection,
        RetirementProjection retirementProjection,
        TaxProjection taxProjection,
        RealEstateProjection realEstateProjection,
        OverviewParameters parameters) {

    public OverviewInput {
        Objects.requireNonNull(treasuryProjection, "treasuryProjection");
        Objects.requireNonNull(patrimoineProjection, "patrimoineProjection");
        Objects.requireNonNull(retirementProjection, "retirementProjection");
        Objects.requireNonNull(taxProjection, "taxProjection");
        Objects.requireNonNull(realEstateProjection, "realEstateProjection");
        Objects.requireNonNull(parameters, "parameters");
    }
}
