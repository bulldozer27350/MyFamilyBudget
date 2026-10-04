package com.moe.myfamilybudget.server.internal.architecture;

import java.util.List;

/**
 * Liste fermée et datée des classes de production autorisées à dépendre de
 * {@code BudgetDataModel} (SILO-002, voir doc/architecture/21-plan-silotage.md).
 *
 * <p>Règles de maintenance :
 * <ul>
 *   <li>la liste ne peut que décroître : ajouter une entrée est interdit (le test
 *       {@link BudgetDataModelUsageArchTest} échoue si la taille dépasse {@link #FROZEN_SIZE}) ;</li>
 *   <li>chaque entrée porte le patch SILO-xxx qui la supprime ;</li>
 *   <li>le patch qui retire l'usage supprime la ligne correspondante et abaisse {@link #FROZEN_SIZE} ;</li>
 *   <li>une entrée dont la classe n'existe plus fait échouer le test.</li>
 * </ul>
 */
final class BudgetDataModelAllowList {

    /** Date de gel de la liste. */
    static final String FROZEN_ON = "2026-10-04";

    /** Taille maximale de la liste à la date de gel ; à abaisser à chaque suppression d'entrée. */
    static final int FROZEN_SIZE = 22;

    /** Entrée : classe de premier niveau autorisée et patch qui retire son usage. */
    record Entry(String className, String removedBy) {}

    private static final String APP = "com.moe.myfamilybudget.application.";
    private static final String PERS = "com.moe.myfamilybudget.persistence.";
    private static final String SRV = "com.moe.myfamilybudget.server.internal.";
    private static final String TRANS = "com.moe.myfamilybudget.transition.";

    static final List<Entry> ENTRIES = List.of(
            // application : factories
            new Entry(APP + "factory.LoanAdviceInputFactory", "SILO-116"),
            new Entry(APP + "factory.OverviewInputFactory", "SILO-117"),
            // application : mappers
            new Entry(APP + "mapper.OverviewMapper", "SILO-119"),
            // application : services et snapshot global
            new Entry(APP + "service.OverviewServiceImpl", "SILO-117"),
            new Entry(APP + "snapshot.GlobalBudgetSnapshotService", "SILO-119"),
            // server : reliquats
            new Entry(SRV + "factory.PlacementRateSuggestionInputFactory", "SILO-116"),
            new Entry(SRV + "impl.AnalysePretsServiceImpl", "SILO-116"),
            // transition-snapshot : port d'écriture du snapshot global
            new Entry(TRANS + "port.GlobalBudgetSnapshotWriter", "SILO-119"),
            // persistence : adaptateurs de lecture par cache global
            new Entry(PERS + "adapter.BudgetPersistenceAdapter", "SILO-216"),
            new Entry(PERS + "adapter.GoalPersistenceAdapter", "SILO-212"),
            new Entry(PERS + "adapter.LoanPersistenceAdapter", "SILO-214"),
            new Entry(PERS + "adapter.PatrimoinePersistenceAdapter", "SILO-215"),
            new Entry(PERS + "adapter.RetirementPersistenceAdapter", "SILO-210"),
            new Entry(PERS + "adapter.SettingsPersistenceAdapter", "SILO-220"),
            new Entry(PERS + "adapter.TaxPersistenceAdapter", "SILO-211"),
            // persistence : écriture du snapshot global
            new Entry(PERS + "adapter.GlobalBudgetSnapshotWriterAdapter", "SILO-119"),
            // persistence : cache, mutations et conversion globales
            new Entry(PERS + "BudgetCacheStore", "SILO-230"),
            new Entry(PERS + "BudgetMutationService", "SILO-230"),
            new Entry(PERS + "BudgetPersistenceGateway", "SILO-230"),
            new Entry(PERS + "PersistenceManager", "SILO-230"),
            new Entry(PERS + "converter.EntityModelConverter", "SILO-230"),
            new Entry(PERS + "updater.TresorerieFieldUpdateDispatcher", "SILO-230"));

    private BudgetDataModelAllowList() {}
}
