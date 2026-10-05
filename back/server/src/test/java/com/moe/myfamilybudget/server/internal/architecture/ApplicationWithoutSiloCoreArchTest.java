package com.moe.myfamilybudget.server.internal.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;

/**
 * Garde-fou SILO-150 à SILO-160 (voir doc/architecture/21-plan-silotage.md) : {@code application} ne connaît que
 * les API des silos ({@code *-api}), jamais leur cœur ({@code com.moe.myfamilybudget.domain.<silo>.core}). Les
 * moteurs sont fournis par injection ; seuls le composition root ({@code DomainEngineConfig}, dans
 * {@code server}) et les tests les référencent.
 *
 * <p>Depuis SILO-160, une seule règle couvre tous les silos, y compris ceux créés plus tard : elle remplace les
 * règles par silo ajoutées de SILO-150 à SILO-159. Un test de couverture vérifie que les cœurs connus sont bien
 * dans le périmètre analysé, pour que la règle ne devienne pas vide sans que personne ne s'en aperçoive.
 */
@AnalyzeClasses(packages = "com.moe.myfamilybudget", importOptions = ImportOption.DoNotIncludeTests.class)
class ApplicationWithoutSiloCoreArchTest {

    private static final List<String> SILO_CORE_PACKAGES = List.of(
            "com.moe.myfamilybudget.domain.retirement.core",
            "com.moe.myfamilybudget.domain.tax.core",
            "com.moe.myfamilybudget.domain.wealth.core",
            "com.moe.myfamilybudget.domain.treasury.core",
            "com.moe.myfamilybudget.domain.bankpointage.core",
            "com.moe.myfamilybudget.domain.analysis.core",
            "com.moe.myfamilybudget.domain.credit.core",
            "com.moe.myfamilybudget.domain.goals.core",
            "com.moe.myfamilybudget.domain.notifications.core",
            "com.moe.myfamilybudget.domain.market.core");

    @ArchTest
    static final ArchRule APPLICATION_DOES_NOT_USE_ANY_SILO_CORE = noClasses()
            .that().resideInAPackage("com.moe.myfamilybudget.application..")
            .should().dependOnClassesThat().resideInAPackage("com.moe.myfamilybudget.domain.*.core..")
            .as("application ne dépend du cœur d'aucun silo (SILO-150 à SILO-160)");

    @ArchTest
    static final ArchRule ENABLE_BANKING_CORE_DOES_NOT_USE_SERVER_APPLICATION_OR_PERSISTENCE = noClasses()
            .that().resideInAPackage("com.moe.myfamilybudget.domain.bankpointage.core.enablebanking..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.moe.myfamilybudget.server..", "com.moe.myfamilybudget.application..",
                    "com.moe.myfamilybudget.persistence..", "org.springframework..")
            .as("l'intégration Enable Banking du silo Banque ne dépend ni de server, ni d'application, "
                    + "ni de la persistance, ni de Spring (SILO-170)");

    @ArchTest
    static final ArchRule NOTIFICATIONS_CORE_DOES_NOT_USE_SERVER_APPLICATION_PERSISTENCE_OR_SPRING = noClasses()
            .that().resideInAPackage("com.moe.myfamilybudget.domain.notifications.core..")
            .and().resideOutsideOfPackage("com.moe.myfamilybudget.domain.notifications.core.persistence..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.moe.myfamilybudget.server..", "com.moe.myfamilybudget.application..",
                    "com.moe.myfamilybudget.persistence..", "org.springframework..",
                    "com.moe.myfamilybudget.domain.bankpointage..", "com.moe.myfamilybudget.domain.goals..",
                    "com.moe.myfamilybudget.domain.wealth..", "com.moe.myfamilybudget.domain.treasury..")
            .as("le cœur du silo Notifications (dispatch, paramètres, canal Web Push ; hors sous-package "
                    + "persistence, SILO-217) ne dépend ni de server, ni d'application, ni de la persistance, "
                    + "ni de Spring, ni d'un autre silo (SILO-180)");

    @ArchTest
    static void known_silo_cores_are_in_the_analyzed_scope(JavaClasses classes) {
        for (String corePackage : SILO_CORE_PACKAGES) {
            assertTrue(classes.containPackage(corePackage),
                    "cœur de silo absent du périmètre analysé (règle vide ?) : " + corePackage);
        }
    }
}
