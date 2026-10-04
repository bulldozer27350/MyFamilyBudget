package com.moe.myfamilybudget.server.internal.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.moe.myfamilybudget.application.mapper.AnalyseMapper;
import com.moe.myfamilybudget.transition.model.BudgetDataModel;

/**
 * Garde-fou RES-010 (voir doc/architecture/19-backlog-pre-maven-patchs.md) : le
 * {@link BudgetDataModel} est un snapshot global, pas une dépendance implicite des résultats
 * métier ni des mappers de façade.
 *
 * <p>Règles strictes (sans gel). Les assemblers applicatifs ({@code internal.factory}, services
 * d'API) et les opérations transverses (snapshot {@code /budget}, désormais par fragments de silo,
 * SILO-119) peuvent encore utiliser {@code BudgetDataModel} pendant la transition : ils
 * relèvent de ARCH-010 et CLEAN-010/020.
 */
@AnalyzeClasses(packages = "com.moe.myfamilybudget", importOptions = ImportOption.DoNotIncludeTests.class)
class ResultModelsArchTest {

    @ArchTest
    static final ArchRule RESULT_MODELS_DO_NOT_DEPEND_ON_BUDGET_DATA_MODEL = noClasses()
            .that().resideInAnyPackage("..internal.model..", "com.moe.myfamilybudget.application.model..")
            .and().haveSimpleNameEndingWith("ResultModel")
            .should().dependOnClassesThat().areAssignableTo(BudgetDataModel.class)
            .as("aucun ResultModel métier ne doit dépendre de BudgetDataModel (RES-010)");

    @ArchTest
    static final ArchRule ANALYSE_MAPPER_DOES_NOT_DEPEND_ON_BUDGET_DATA_MODEL = noClasses()
            .that().areAssignableTo(AnalyseMapper.class)
            .should().dependOnClassesThat().areAssignableTo(BudgetDataModel.class)
            .as("AnalyseMapper reçoit une BudgetFacadeView, pas le snapshot global BudgetDataModel (RES-010)");
}
