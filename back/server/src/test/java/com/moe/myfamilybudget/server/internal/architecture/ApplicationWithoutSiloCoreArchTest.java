package com.moe.myfamilybudget.server.internal.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Garde-fou SILO-150 (voir doc/architecture/21-plan-silotage.md) : {@code application} ne connaît que l'API du
 * silo Retraite ({@code retirement-api}), jamais son cœur ({@code com.moe.myfamilybudget.domain.retirement.core}).
 * Le moteur est fourni par injection ; seuls le composition root et les tests le référencent. La règle est
 * étendue silo par silo (Fiscalité : SILO-151 ; Patrimoine : SILO-152 ; autres silos : SILO-153 à SILO-159) (puis généralisée par SILO-160).
 */
@AnalyzeClasses(packages = "com.moe.myfamilybudget", importOptions = ImportOption.DoNotIncludeTests.class)
class ApplicationWithoutSiloCoreArchTest {

    @ArchTest
    static final ArchRule APPLICATION_DOES_NOT_USE_RETIREMENT_CORE = noClasses()
            .that().resideInAPackage("com.moe.myfamilybudget.application..")
            .should().dependOnClassesThat().resideInAPackage("com.moe.myfamilybudget.domain.retirement.core..")
            .as("application ne dépend pas du cœur du silo Retraite (SILO-150)");

    @ArchTest
    static final ArchRule APPLICATION_DOES_NOT_USE_TAX_CORE = noClasses()
            .that().resideInAPackage("com.moe.myfamilybudget.application..")
            .should().dependOnClassesThat().resideInAPackage("com.moe.myfamilybudget.domain.tax.core..")
            .as("application ne dépend pas du cœur du silo Fiscalité (SILO-151)");

    @ArchTest
    static final ArchRule APPLICATION_DOES_NOT_USE_WEALTH_CORE = noClasses()
            .that().resideInAPackage("com.moe.myfamilybudget.application..")
            .should().dependOnClassesThat().resideInAPackage("com.moe.myfamilybudget.domain.wealth.core..")
            .as("application ne dépend pas du cœur du silo Patrimoine (SILO-152)");
}
