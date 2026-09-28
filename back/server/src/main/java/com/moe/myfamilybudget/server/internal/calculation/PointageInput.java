package com.moe.myfamilybudget.server.internal.calculation;

import java.util.Collections;
import java.util.List;

import com.moe.myfamilybudget.server.internal.model.BankImportModel;

/**
 * Contrat d'entrée du domaine Pointage (RF-500, voir doc/architecture/07-domaine-banque-pointage.md).
 *
 * <p>Remplace à terme {@code PointageModel}, qui agrège directement charges, revenus, placements
 * et paramètres : Pointage ne reçoit plus que des lignes budgétaires déjà composées
 * ({@link BudgetLineProjection}), les transactions et les liens de rapprochement du mois.
 *
 * <p>Les transactions et liens réutilisent volontairement les types de {@code BankImportModel}
 * (calculateur déjà pur, à ne pas dupliquer). Aucun {@code BankImportInput} global n'est introduit.
 *
 * @param transactions      transactions bancaires prises en compte
 * @param matchings         liens de rapprochement (ligne budgétaire / transactions) du mois
 * @param activeBudgetLines lignes budgétaires actives pour {@code period}
 * @param period            mois pointé
 */
public record PointageInput(
        List<BankImportModel.BankTransactionModel> transactions,
        List<BankImportModel.MatchingLinkModel> matchings,
        List<BudgetLineProjection> activeBudgetLines,
        PointagePeriod period) {

    public PointageInput {
        if (transactions == null) transactions = Collections.emptyList();
        if (matchings == null) matchings = Collections.emptyList();
        if (activeBudgetLines == null) activeBudgetLines = Collections.emptyList();
        if (period == null) period = new PointagePeriod("");
    }
}
