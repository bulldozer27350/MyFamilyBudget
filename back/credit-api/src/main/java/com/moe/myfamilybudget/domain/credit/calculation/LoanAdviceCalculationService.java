package com.moe.myfamilybudget.domain.credit.calculation;

import java.time.LocalDate;

import com.moe.myfamilybudget.domain.credit.model.LoanAdviceResultModel;

/**
 * Interface de service du silo Crédit (SILO-156, voir doc/architecture/21-plan-silotage.md).
 *
 * <p>Seule vue du moteur d'analyse des prêts pour les consommateurs ({@code server}, tests) : ils ne connaissent
 * ni son implémentation ({@code DefaultLoanAdviceCalculationService}, dans {@code credit-core}), ni son câblage
 * (déclaré par le composition root). Reçoit exclusivement {@link LoanAdviceInput} : aucun {@code BudgetDataModel},
 * aucun autre modèle persistant.
 */
public interface LoanAdviceCalculationService {

    /** Analyse chaque prêt : solder plus vite, renégocier ou conserver. */
    LoanAdviceResultModel compute(LoanAdviceInput input);

    /**
     * Capital restant dû à une date, projeté depuis la date de référence du prêt (un pas par mois, du mois de
     * référence au mois cible inclus), comme {@code projectLoanCrdToDate()} de calculations.js.
     */
    double projectCrd(LoanInput loan, LocalDate target);

    /** Lit une date {@code yyyy-MM-dd} ou {@code yyyy-MM} ; {@code null} si le texte est absent ou illisible. */
    LocalDate parseDate(String text);
}
