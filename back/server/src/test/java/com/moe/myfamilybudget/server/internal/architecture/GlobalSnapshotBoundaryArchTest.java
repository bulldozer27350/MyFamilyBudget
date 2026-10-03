package com.moe.myfamilybudget.server.internal.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.moe.myfamilybudget.application.service.SystemeServiceImpl;
import com.moe.myfamilybudget.transition.model.BudgetDataModel;
import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.transition.port.GlobalBudgetSnapshotWriter;

/**
 * Garde-fou CLEAN-020 (voir doc/architecture/19-backlog-pre-maven-patchs.md) : les opérations qui
 * remplacent ou réinitialisent le snapshot global (import, reset, restauration) sont isolées dans
 * {@code application.snapshot} (MAVEN-103), qui écrit via le port {@link GlobalBudgetSnapshotWriter}
 * implémenté par {@code persistence}. Aucun service métier local ne peut les appeler.
 */
@AnalyzeClasses(packages = "com.moe.myfamilybudget", importOptions = ImportOption.DoNotIncludeTests.class)
class GlobalSnapshotBoundaryArchTest {

    @ArchTest
    static final ArchRule ONLY_SNAPSHOT_COMPONENT_REPLACES_THE_GLOBAL_SNAPSHOT = noClasses()
            .that().resideOutsideOfPackages("com.moe.myfamilybudget.persistence..")
            .should().callMethod(PersistenceManager.class, "setBudgetData", BudgetDataModel.class)
            .as("seul le module persistence (adaptateur du port GlobalBudgetSnapshotWriter) remplace le snapshot global via setBudgetData (CLEAN-020)");

    @ArchTest
    static final ArchRule ONLY_SNAPSHOT_COMPONENT_RESETS_THE_GLOBAL_SNAPSHOT = noClasses()
            .that().resideOutsideOfPackages("com.moe.myfamilybudget.persistence..")
            .should().callMethod(PersistenceManager.class, "resetData")
            .as("seul le module persistence (adaptateur du port GlobalBudgetSnapshotWriter) réinitialise le snapshot global via resetData (CLEAN-020)");

    @ArchTest
    static final ArchRule SYSTEME_CONTROLLER_DOES_NOT_TOUCH_SNAPSHOT_OR_PERSISTENCE = noClasses()
            .that().areAssignableTo(SystemeServiceImpl.class)
            .should().dependOnClassesThat().areAssignableTo(BudgetDataModel.class)
            .orShould().dependOnClassesThat().areAssignableTo(PersistenceManager.class)
            .as("SystemeServiceImpl délègue à GlobalBudgetSnapshotService sans BudgetDataModel ni PersistenceManager (CLEAN-020)");

    @ArchTest
    static final ArchRule ONLY_SNAPSHOT_COMPONENT_USES_THE_SNAPSHOT_WRITER = noClasses()
            .that().resideOutsideOfPackages("com.moe.myfamilybudget.persistence..",
                    "com.moe.myfamilybudget.application.snapshot..")
            .should().dependOnClassesThat().areAssignableTo(GlobalBudgetSnapshotWriter.class)
            .as("seul application.snapshot utilise le port GlobalBudgetSnapshotWriter (import, reset ; CLEAN-020, MAVEN-103)");
}
