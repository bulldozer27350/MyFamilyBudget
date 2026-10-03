package com.moe.myfamilybudget.server.internal.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.moe.myfamilybudget.transition.model.BudgetDataModel;

/**
 * Garde-fou CLEAN-010 (voir doc/architecture/19-backlog-pre-maven-patchs.md et
 * doc/architecture/19-inventaire-budget-data-model.md) : {@link BudgetDataModel} est un snapshot
 * global, pas un « DTO interne universel ».
 *
 * <p>Liste blanche explicite des seuls consommateurs autorisés pendant la transition :
 * persistance (snapshot global et cache), assemblers applicatifs ({@code internal.factory}, {@code application.factory}),
 * mappers de façade ({@code internal.mapper}, {@code application.mapper}), mutation transverse ({@code persistence.updater}, module {@code persistence}),
 * opérations globales isolées par CLEAN-020 ({@code internal.snapshot}) et
 * les services d'API recensés dans l'inventaire (assemblage {@code ASSEMBLY-TEMP} ou opérations
 * {@code SNAPSHOT-GLOBAL}). Tout nouveau consommateur fait échouer la règle : il doit soit
 * consommer des fragments via les {@code Reader}, soit être ajouté à l'inventaire avec sa
 * classification (sans gel de dette).
 */
@AnalyzeClasses(packages = "com.moe.myfamilybudget", importOptions = ImportOption.DoNotIncludeTests.class)
class BudgetDataModelUsageArchTest {

    private static final String ALLOWED_API_SERVICES =
            ".*\\.(internal\\.impl|application\\.service)\\.("
                    + "AnalysePretsServiceImpl|AnalyseServiceImpl|ImpotsServiceImpl|OverviewServiceImpl"
                    + "|PatrimoineServiceImpl|PendingOperationsServiceImpl|RetraiteServiceImpl"
                    + "|TresorerieServiceImpl"
                    + ")(\\$.*)?";

    @ArchTest
    static final ArchRule BUDGET_DATA_MODEL_IS_ONLY_USED_BY_ALLOWED_CONSUMERS = noClasses()
            .that().areNotAssignableTo(BudgetDataModel.class)
            .and().resideOutsideOfPackages(
                    "com.moe.myfamilybudget.persistence..",
                    "..internal.factory..",
                    "com.moe.myfamilybudget.application.factory..",
                    "..internal.mapper..",
                    "com.moe.myfamilybudget.application.mapper..",
                    "..internal.snapshot..")
            .and().haveNameNotMatching(ALLOWED_API_SERVICES)
            .should().dependOnClassesThat().areAssignableTo(BudgetDataModel.class)
            .as("BudgetDataModel n'est consommé que par les composants listés dans l'inventaire CLEAN-010");
}
