package com.moe.myfamilybudget.server.internal.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.moe.myfamilybudget.transition.model.BudgetDataModel;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Garde-fou SILO-120 (voir doc/architecture/21-plan-silotage.md) : {@code BudgetDataModel} est supprimé
 * d'{@code application} et de {@code server}. Seuls {@code persistence} (jusqu'à SILO-230) et le module
 * {@code transition-snapshot} qui le définit peuvent encore le connaître.
 *
 * <p>Cette règle est volontairement indépendante de {@link BudgetDataModelAllowList} : même si la liste
 * fermée de SILO-002 était modifiée, aucune classe de production d'{@code application} ou de {@code server}
 * ne peut redépendre du snapshot global.
 */
@AnalyzeClasses(packages = "com.moe.myfamilybudget", importOptions = ImportOption.DoNotIncludeTests.class)
class ApplicationAndServerWithoutBudgetDataModelArchTest {

    private static final String PERSISTENCE_PREFIX = "com.moe.myfamilybudget.persistence.";

    @ArchTest
    static final ArchRule APPLICATION_AND_SERVER_DO_NOT_USE_BUDGET_DATA_MODEL = noClasses()
            .that().resideInAnyPackage("com.moe.myfamilybudget.application..", "com.moe.myfamilybudget.server..")
            .should().dependOnClassesThat().areAssignableTo(BudgetDataModel.class)
            .as("application et server ne dépendent plus de BudgetDataModel (SILO-120)");

    @Test
    public void allow_list_only_contains_persistence_classes() {
        for (BudgetDataModelAllowList.Entry entry : BudgetDataModelAllowList.ENTRIES) {
            if (!entry.className().startsWith(PERSISTENCE_PREFIX)) {
                throw new AssertionError("BudgetDataModelAllowList ne doit plus contenir que des classes de persistence (SILO-120) : "
                        + entry.className());
            }
        }
    }
}
