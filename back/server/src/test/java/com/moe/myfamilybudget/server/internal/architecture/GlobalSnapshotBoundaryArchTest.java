package com.moe.myfamilybudget.server.internal.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.moe.myfamilybudget.server.internal.impl.SystemeServiceImpl;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

/**
 * Garde-fou CLEAN-020 (voir doc/architecture/19-backlog-pre-maven-patchs.md) : les opérations qui
 * remplacent ou réinitialisent le snapshot global (import, reset, restauration) sont isolées dans
 * {@code internal.snapshot}. Aucun service métier local ne peut les appeler.
 */
@AnalyzeClasses(packages = "com.moe.myfamilybudget", importOptions = ImportOption.DoNotIncludeTests.class)
class GlobalSnapshotBoundaryArchTest {

    @ArchTest
    static final ArchRule ONLY_SNAPSHOT_COMPONENT_REPLACES_THE_GLOBAL_SNAPSHOT = noClasses()
            .that().resideOutsideOfPackages("..internal.persistence..", "..internal.snapshot..")
            .should().callMethod(PersistenceManager.class, "setBudgetData", BudgetDataModel.class)
            .as("seul internal.snapshot remplace le snapshot global via setBudgetData (CLEAN-020)");

    @ArchTest
    static final ArchRule ONLY_SNAPSHOT_COMPONENT_RESETS_THE_GLOBAL_SNAPSHOT = noClasses()
            .that().resideOutsideOfPackages("..internal.persistence..", "..internal.snapshot..")
            .should().callMethod(PersistenceManager.class, "resetData")
            .as("seul internal.snapshot réinitialise le snapshot global via resetData (CLEAN-020)");

    @ArchTest
    static final ArchRule SYSTEME_CONTROLLER_DOES_NOT_TOUCH_SNAPSHOT_OR_PERSISTENCE = noClasses()
            .that().areAssignableTo(SystemeServiceImpl.class)
            .should().dependOnClassesThat().areAssignableTo(BudgetDataModel.class)
            .orShould().dependOnClassesThat().areAssignableTo(PersistenceManager.class)
            .as("SystemeServiceImpl délègue à GlobalBudgetSnapshotService sans BudgetDataModel ni PersistenceManager (CLEAN-020)");
}
