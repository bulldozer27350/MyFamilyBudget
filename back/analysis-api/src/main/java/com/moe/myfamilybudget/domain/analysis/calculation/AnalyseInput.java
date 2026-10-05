package com.moe.myfamilybudget.domain.analysis.calculation;

import java.util.Collections;
import java.util.List;

/**
 * Contrat d'entrée du domaine Analyse (RF-600, voir doc/architecture/08-domaine-analyse.md).
 *
 * <p>Analyse ne reçoit que des transactions, catégories, rapprochements et opérations en cours
 * sous des types qui lui sont propres ({@link AnalysisTransaction}, {@link AnalysisCategory},
 * {@link AnalysisMatching}, {@link AnalysisPendingOperation}), ainsi que des lignes budgétaires déjà
 * composées mois par mois ({@link MonthlyBudgetLines}, voir {@link AnalysisBudgetLine}) et leur nature
 * ({@link BudgetLineKind}). Depuis SILO-133, le silo ne référence aucun type des silos Banque/Pointage et
 * Budget : l'application traduit. Aucun {@code SettingsModel} ni modèle du budget n'est transporté.
 *
 * <p>Ce contrat n'inclut volontairement pas de performance des placements : aucun code Java ne la
 * consomme aujourd'hui (seule la nature d'une ligne, via {@link BudgetLineKind}, provient des
 * placements). Un tel snapshot sera ajouté par le patch qui en introduira l'usage.
 *
 * @param period             période d'analyse (date du jour, profondeur de l'historique)
 * @param transactions       transactions bancaires prises en compte
 * @param categories         catégories bancaires (libellé, nature, compressible)
 * @param matchings          liens de rapprochement, tous les mois confondus
 * @param pendingOperations  opérations en cours (non encore importées en transaction bancaire)
 * @param monthlyBudgetLines lignes budgétaires actives, une entrée par mois de la fenêtre d'analyse
 * @param lineKinds          nature de chaque ligne budgétaire connue (charge, revenu, placement)
 */
public record AnalyseInput(
        AnalysisPeriod period,
        List<AnalysisTransaction> transactions,
        List<AnalysisCategory> categories,
        List<AnalysisMatching> matchings,
        List<AnalysisPendingOperation> pendingOperations,
        List<MonthlyBudgetLines> monthlyBudgetLines,
        List<BudgetLineKind> lineKinds) {

    public AnalyseInput {
        if (period == null) period = new AnalysisPeriod(null, 12);
        if (transactions == null) transactions = Collections.emptyList();
        if (categories == null) categories = Collections.emptyList();
        if (matchings == null) matchings = Collections.emptyList();
        if (pendingOperations == null) pendingOperations = Collections.emptyList();
        if (monthlyBudgetLines == null) monthlyBudgetLines = Collections.emptyList();
        if (lineKinds == null) lineKinds = Collections.emptyList();
    }
}
