package com.moe.myfamilybudget.server.internal.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Garde-fou SILO-210 à SILO-212 (voir doc/architecture/21-plan-silotage.md) : la persistance JPA d'un silo
 * ({@code com.moe.myfamilybudget.domain.<silo>.core.persistence}) ne connaît ni la persistance de transition, ni
 * {@code application}, ni {@code server}, ni le snapshot global, ni le contrat REST, ni un autre silo.
 */
@AnalyzeClasses(packages = "com.moe.myfamilybudget", importOptions = ImportOption.DoNotIncludeTests.class)
class SiloPersistenceIsolationArchTest {

    private static final String SILO_PERSISTENCE = "com.moe.myfamilybudget.domain.*.core.persistence..";

    /** Silos dont la persistance a été déplacée dans leur cœur (un silo de plus par patch SILO-21x). */
    private static final List<String> SILOS_WITH_OWN_PERSISTENCE = List.of(
            "com.moe.myfamilybudget.domain.retirement.core.persistence",
            "com.moe.myfamilybudget.domain.tax.core.persistence",
            "com.moe.myfamilybudget.domain.goals.core.persistence");

    @ArchTest
    static final ArchRule SILO_PERSISTENCE_DOES_NOT_USE_TRANSITION_LAYERS = noClasses()
            .that().resideInAPackage(SILO_PERSISTENCE)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.moe.myfamilybudget.persistence..", "com.moe.myfamilybudget.application..",
                    "com.moe.myfamilybudget.server..", "com.moe.myfamilybudget.config..",
                    "com.moe.myfamilybudget.transition..", "com.moe.myfamilybudget.api..")
            .as("la persistance d'un silo ne dépend ni de persistence, ni d'application, ni de server, "
                    + "ni de transition-snapshot, ni de l'API REST (SILO-210)");

    @ArchTest
    static final ArchRule RETIREMENT_PERSISTENCE_DOES_NOT_USE_OTHER_SILOS = noClasses()
            .that().resideInAPackage("com.moe.myfamilybudget.domain.retirement.core.persistence..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.moe.myfamilybudget.domain.tax..", "com.moe.myfamilybudget.domain.wealth..",
                    "com.moe.myfamilybudget.domain.treasury..", "com.moe.myfamilybudget.domain.bankpointage..",
                    "com.moe.myfamilybudget.domain.analysis..", "com.moe.myfamilybudget.domain.credit..",
                    "com.moe.myfamilybudget.domain.goals..", "com.moe.myfamilybudget.domain.notifications..",
                    "com.moe.myfamilybudget.domain.market..")
            .as("la persistance du silo Retraite ne dépend d'aucun autre silo (D1, SILO-210)");

    @ArchTest
    static final ArchRule TAX_PERSISTENCE_DOES_NOT_USE_OTHER_SILOS = noClasses()
            .that().resideInAPackage("com.moe.myfamilybudget.domain.tax.core.persistence..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.moe.myfamilybudget.domain.retirement..", "com.moe.myfamilybudget.domain.wealth..",
                    "com.moe.myfamilybudget.domain.treasury..", "com.moe.myfamilybudget.domain.bankpointage..",
                    "com.moe.myfamilybudget.domain.analysis..", "com.moe.myfamilybudget.domain.credit..",
                    "com.moe.myfamilybudget.domain.goals..", "com.moe.myfamilybudget.domain.notifications..",
                    "com.moe.myfamilybudget.domain.market..")
            .as("la persistance du silo Fiscalité ne dépend d'aucun autre silo (D1, SILO-211)");

    @ArchTest
    static final ArchRule GOALS_PERSISTENCE_DOES_NOT_USE_OTHER_SILOS = noClasses()
            .that().resideInAPackage("com.moe.myfamilybudget.domain.goals.core.persistence..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.moe.myfamilybudget.domain.retirement..", "com.moe.myfamilybudget.domain.tax..",
                    "com.moe.myfamilybudget.domain.wealth..", "com.moe.myfamilybudget.domain.treasury..",
                    "com.moe.myfamilybudget.domain.bankpointage..", "com.moe.myfamilybudget.domain.analysis..",
                    "com.moe.myfamilybudget.domain.credit..", "com.moe.myfamilybudget.domain.notifications..",
                    "com.moe.myfamilybudget.domain.market..")
            .as("la persistance du silo Objectifs ne dépend d'aucun autre silo (D1, SILO-212)");

    @ArchTest
    static void silo_persistence_packages_are_in_the_analyzed_scope(JavaClasses classes) {
        for (String persistencePackage : SILOS_WITH_OWN_PERSISTENCE) {
            assertTrue(classes.containPackage(persistencePackage),
                    "persistance de silo absente du périmètre analysé (règle vide ?) : " + persistencePackage);
        }
    }
}
