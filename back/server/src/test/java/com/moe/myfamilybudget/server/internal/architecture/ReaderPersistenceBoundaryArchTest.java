package com.moe.myfamilybudget.server.internal.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.moe.myfamilybudget.persistence.PersistenceManager;

/**
 * Garde-fou DB-1070 (voir doc/architecture/18-backlog-persistance-patchs.md) : apres la bascule JPA des
 * domaines, aucun service applicatif ne doit revenir a {@code PersistenceManager.getBudgetData()} (ou a
 * l'un des composants du hub) pour compenser une migration. Les lectures passent par les ports
 * {@code *Reader}, les ecritures par les command services et leurs ports {@code *Writer}.
 *
 * <p>Regles strictes, sans gel : toute nouvelle violation fait echouer le build. Complete
 * {@link GlobalSnapshotBoundaryArchTest} (CLEAN-020), qui isole {@code setBudgetData} et {@code resetData}.
 */
@AnalyzeClasses(packages = "com.moe.myfamilybudget", importOptions = ImportOption.DoNotIncludeTests.class)
class ReaderPersistenceBoundaryArchTest {

    /** Composants internes du hub, hors {@code PersistenceManager} (traite par la regle suivante). */
    private static final String HUB_INTERNALS =
            "com\\.moe\\.myfamilybudget\\.persistence\\.("
                    + "BudgetCacheStore|BudgetPersistenceGateway|BudgetMutationService|DomainMutations"
                    + "|entity\\.BudgetDataEntity|repository\\.BudgetDataRepository"
                    + "|converter\\.EntityModelConverter"
                    + ")(\\$.*)?";

    @ArchTest
    static final ArchRule ONLY_PERSISTENCE_USES_THE_HUB_INTERNALS = noClasses()
            .that().resideOutsideOfPackages("com.moe.myfamilybudget.persistence..")
            .should().dependOnClassesThat().haveNameMatching(HUB_INTERNALS)
            .as("seul le module persistence depend du cache, de la passerelle, des mutations et du hub (DB-1070)");

    @ArchTest
    static final ArchRule ONLY_PERSISTENCE_USES_PERSISTENCE_MANAGER = noClasses()
            .that().resideOutsideOfPackages("com.moe.myfamilybudget.persistence..")
            .should().dependOnClassesThat().areAssignableTo(PersistenceManager.class)
            .as("seul le module persistence depend de PersistenceManager (DB-1070, MAVEN-103 : le snapshot global passe par les ports replace/reset de chaque silo, SILO-119)");

    @ArchTest
    static final ArchRule NOBODY_OUTSIDE_PERSISTENCE_READS_THE_GLOBAL_BUDGET = noClasses()
            .that().resideOutsideOfPackages("com.moe.myfamilybudget.persistence..")
            .should().callMethod(PersistenceManager.class, "getBudgetData")
            .orShould().callMethod(PersistenceManager.class, "getBankImport")
            .as("aucune lecture du budget global via PersistenceManager hors persistance : passer par les Reader (DB-1070)");
}
