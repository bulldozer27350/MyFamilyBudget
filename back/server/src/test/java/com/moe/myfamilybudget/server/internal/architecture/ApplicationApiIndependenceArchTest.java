package com.moe.myfamilybudget.server.internal.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Garde-fou SILO-300 (voir doc/architecture/21-plan-silotage.md) : l'API applicative ({@code application-api},
 * packages {@code com.moe.myfamilybudget.application.port} et
 * {@code com.moe.myfamilybudget.application.usecase.retirement}) ne connait ni Spring, ni JPA, ni le contrat REST
 * genere, ni l'implementation de l'application, ni la persistance, ni le module {@code server}, ni les silos.
 * L'implementation des cas d'usage ({@code com.moe.myfamilybudget.application.usecase}, module {@code application})
 * n'est pas dans le perimetre : elle est le lieu de la traduction entre types des silos et modeles applicatifs.
 */
@AnalyzeClasses(packages = "com.moe.myfamilybudget", importOptions = ImportOption.DoNotIncludeTests.class)
class ApplicationApiIndependenceArchTest {

    @ArchTest
    static final ArchRule APPLICATION_API_IS_SELF_CONTAINED = noClasses()
            .that().resideInAnyPackage("com.moe.myfamilybudget.application.port..",
                    "com.moe.myfamilybudget.application.usecase.retirement..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "org.hibernate..",
                    "com.moe.myfamilybudget.api..",
                    "com.moe.myfamilybudget.application.service..", "com.moe.myfamilybudget.application.command..",
                    "com.moe.myfamilybudget.application.factory..", "com.moe.myfamilybudget.application.mapper..",
                    "com.moe.myfamilybudget.application.model..", "com.moe.myfamilybudget.application.snapshot..",
                    "com.moe.myfamilybudget.application.usecase",
                    "com.moe.myfamilybudget.application.settings..", "com.moe.myfamilybudget.application.error..",
                    "com.moe.myfamilybudget.persistence..", "com.moe.myfamilybudget.config..",
                    "com.moe.myfamilybudget.server..", "com.moe.myfamilybudget.transition..",
                    "com.moe.myfamilybudget.domain..")
            .as("application-api ne depend ni de Spring, ni de JPA, ni de l'OpenAPI genere, ni de l'implementation "
                    + "de l'application, ni de la persistance, ni de server, ni des silos (SILO-300)");

    /** Evite une regle vide : les ports et le cas d'usage Retraite doivent etre dans le perimetre analyse. */
    @Test
    void coversThePortsAndUseCasesOfTheApplicationApi() {
        JavaClasses classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.moe.myfamilybudget.application.port",
                        "com.moe.myfamilybudget.application.usecase.retirement");
        assertThat(classes.stream().map(c -> c.getSimpleName()))
                .contains("TransactionRunner", "SiloMutationLock", "MutationSilo",
                        "RetirementUseCase", "RetraiteResultModel", "RetraitePersonWithProjectionModel");
    }

    /** Le cas d'usage Retraite est implemente dans {@code application}, pas dans {@code application-api}. */
    @Test
    void retirementUseCaseIsImplementedOutsideTheApi() {
        JavaClasses classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.moe.myfamilybudget.application.usecase");
        assertThat(classes.stream().filter(c -> c.getPackageName().equals("com.moe.myfamilybudget.application.usecase"))
                .map(c -> c.getSimpleName()))
                .contains("DefaultRetirementUseCase");
    }
}
