package com.moe.myfamilybudget.server.internal.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Garde-fou SILO-200 (voir doc/architecture/21-plan-silotage.md) : le module {@code infra-jpa} ne porte que de la
 * configuration technique. Aucun type métier : il ne dépend d'aucun silo, ni d'{@code application}, ni de la
 * persistance de transition, ni du composition root.
 */
@AnalyzeClasses(packages = "com.moe.myfamilybudget", importOptions = ImportOption.DoNotIncludeTests.class)
class InfraJpaIndependenceArchTest {

    private static final String INFRA_JPA_PACKAGE = "com.moe.myfamilybudget.infra.jpa";

    @ArchTest
    static final ArchRule INFRA_JPA_HAS_NO_BUSINESS_TYPES = noClasses()
            .that().resideInAPackage(INFRA_JPA_PACKAGE + "..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.moe.myfamilybudget.domain..", "com.moe.myfamilybudget.transition..",
                    "com.moe.myfamilybudget.application..", "com.moe.myfamilybudget.persistence..",
                    "com.moe.myfamilybudget.server..", "com.moe.myfamilybudget.config..",
                    "com.moe.myfamilybudget.api..")
            .as("infra-jpa ne dépend d'aucun silo, ni d'application, ni de la persistance, ni de server (SILO-200)");

    @ArchTest
    static void infra_jpa_is_in_the_analyzed_scope(JavaClasses classes) {
        assertTrue(classes.containPackage(INFRA_JPA_PACKAGE),
                "infra-jpa absent du périmètre analysé (règle vide ?) : " + INFRA_JPA_PACKAGE);
    }
}
