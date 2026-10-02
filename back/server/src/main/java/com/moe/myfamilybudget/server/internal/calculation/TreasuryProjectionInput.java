package com.moe.myfamilybudget.server.internal.calculation;

import java.util.List;
import java.util.Objects;
import com.moe.myfamilybudget.domain.retirement.calculation.RetirementIncomeProjection;

/**
 * Contrat d'entrée de la projection de trésorerie (RF-400, voir
 * doc/architecture/06-domaine-tresorerie.md).
 *
 * <p>Pas de modèle unique reprenant tout {@code BudgetDataModel} : Trésorerie reçoit les
 * résultats/projections déjà calculés des autres domaines ({@code taxProjection},
 * {@code retirementIncome}), jamais leurs modèles source, et des lignes de budget de base
 * normalisées pour le reste ({@code incomes}, {@code charges}, ...) — voir la conclusion du
 * document de domaine.
 *
 * <p><b>Point ouvert tranché</b> (voir 05-domaine-patrimoine.md#point-ouvert) : aucune dépendance
 * retour Trésorerie → Patrimoine n'est introduite ({@code Patrimoine ↔ Trésorerie} fait partie des
 * cycles explicitement interdits par 00-principes.md). {@link #placements()} est une simple somme
 * de versements configurés, indépendante de la simulation de pause du domaine Patrimoine — voir
 * {@link PlacementCashflowInput}.
 *
 * @param period           horizon de simulation
 * @param incomes          revenus réguliers du foyer (hors pensions de retraite, voir {@code retirementIncome})
 * @param charges          charges du foyer
 * @param variableIncomes  revenus variables (primes, bonus...)
 * @param oneOffExpenses   dépenses ponctuelles
 * @param transfers        virements vers/depuis les placements, tous placements confondus
 * @param placements       versements annuels vers les placements, tous placements confondus
 * @param taxProjection    impôt déjà projeté par le domaine Fiscalité
 * @param retirementIncome pensions déjà projetées par le domaine Retraite
 * @param parameters       horizon de retraite, solde de départ et inflation
 */
public record TreasuryProjectionInput(
        TreasurySimulationPeriod period,
        List<IncomeProjectionInput> incomes,
        List<ChargeProjectionInput> charges,
        List<VariableIncomeProjection> variableIncomes,
        List<OneOffCashflow> oneOffExpenses,
        List<TransferProjection> transfers,
        List<PlacementCashflowInput> placements,
        TaxProjection taxProjection,
        RetirementIncomeProjection retirementIncome,
        TreasuryParameters parameters) {

    public TreasuryProjectionInput {
        Objects.requireNonNull(period, "period");
        incomes = incomes != null ? List.copyOf(incomes) : List.of();
        charges = charges != null ? List.copyOf(charges) : List.of();
        variableIncomes = variableIncomes != null ? List.copyOf(variableIncomes) : List.of();
        oneOffExpenses = oneOffExpenses != null ? List.copyOf(oneOffExpenses) : List.of();
        transfers = transfers != null ? List.copyOf(transfers) : List.of();
        placements = placements != null ? List.copyOf(placements) : List.of();
        taxProjection = taxProjection != null ? taxProjection : new TaxProjection(List.of());
        retirementIncome = retirementIncome != null ? retirementIncome : new RetirementIncomeProjection(List.of());
        parameters = Objects.requireNonNull(parameters, "parameters");
    }
}
