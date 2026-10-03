package com.moe.myfamilybudget.server.internal.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameStartingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;

/**
 * Règles ARCH-010 (voir doc/architecture/19-backlog-pre-maven-patchs.md) : interdictions explicites
 * pour les « couches pures », c'est-à-dire les moteurs de calcul ({@code internal.calculation}), les
 * calculateurs et modèles de résultat ({@code internal.model}) et les règles de notification
 * ({@code internal.notification.rules}).
 *
 * <p>Les règles sont définies ici pour être exécutées par {@link PureLayerArchTest} sur le code de
 * production et par {@link PureLayerRulesNegativeTest} sur des fixtures volontairement fautives
 * (vérification négative : une règle qui ne peut pas échouer ne protège rien).
 *
 * <p>Périmètre volontairement limité :
 * <ul>
 *   <li>{@code internal.factory} n'est pas visé : les assemblers/factories peuvent dépendre des
 *       modèles de persistance pendant la transition ;</li>
 *   <li>dans {@code internal.calculation}, les adapters de paramétrage utilisateur
 *       ({@code Jpa*}, {@code *SettingsService}, {@code *SettingsStore}) ne sont pas des moteurs :
 *       ils sont exclus, leur déplacement relève des patchs de persistance ;</li>
 *   <li>seul le package {@code org.springframework.stereotype} est toléré dans les couches pures
 *       (déclaration de composants) ;</li>
 *   <li>{@code BudgetDataModel} vit dans {@code internal.model} et reste libre de se référencer.</li>
 * </ul>
 */
final class PureLayerRules {

    private PureLayerRules() {
    }

    private static final DescribedPredicate<JavaClass> SETTINGS_INFRASTRUCTURE =
            simpleNameStartingWith("Jpa")
                    .or(simpleNameEndingWith("SettingsService"))
                    .or(simpleNameEndingWith("SettingsStore"));

    /** Moteurs, calculateurs, résultats et règles de notification : tout sauf l'infra de paramétrage. */
    static final DescribedPredicate<JavaClass> PURE_LAYER =
            resideInAPackage("..internal.calculation..").and(not(SETTINGS_INFRASTRUCTURE))
                    .or(resideInAPackage("..internal.model.."))
                    .or(resideInAPackage("com.moe.myfamilybudget.domain.budget.."))
                    .or(resideInAPackage("com.moe.myfamilybudget.domain.retirement.."))
                    .or(resideInAPackage("com.moe.myfamilybudget.domain.tax.."))
                    .or(resideInAPackage("com.moe.myfamilybudget.domain.wealth.."))
                    .or(resideInAPackage("..internal.notification.rules.."))
                    .as("les couches pures (internal.calculation hors infra de paramétrage, "
                            + "internal.model, domain.budget, domain.retirement, domain.tax, domain.wealth, internal.notification.rules)");

    static final ArchRule NO_BUDGET_DATA_MODEL = noClasses()
            .that(PURE_LAYER)
            .and().areNotAssignableTo(BudgetDataModel.class)
            .should().dependOnClassesThat().areAssignableTo(BudgetDataModel.class)
            .as("les couches pures ne doivent pas dépendre de BudgetDataModel (ARCH-010)");

    static final ArchRule NO_PERSISTENCE = noClasses()
            .that(PURE_LAYER)
            .should().dependOnClassesThat().resideInAPackage("..internal.persistence..")
            .as("les couches pures ne doivent dépendre ni de PersistenceManager, ni des entités JPA, "
                    + "ni des repositories, ni des adapters (internal.persistence) (ARCH-010)");

    static final ArchRule NO_JPA_API = noClasses()
            .that(PURE_LAYER)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "jakarta.persistence..", "javax.persistence..", "org.springframework.data..")
            .as("les couches pures ne doivent pas utiliser l'API JPA ni Spring Data (ARCH-010)");

    static final ArchRule NO_OPENAPI_DTO = noClasses()
            .that(PURE_LAYER)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.moe.myfamilybudget.api..", "com.moe.myfamilybudget.server.api..")
            .as("les couches pures ne doivent dépendre d'aucun DTO ni contrat OpenAPI (ARCH-010)");

    static final ArchRule SPRING_LIMITED_TO_STEREOTYPES = noClasses()
            .that(PURE_LAYER)
            .should().dependOnClassesThat(
                    resideInAPackage("org.springframework..")
                            .and(resideOutsideOfPackage("org.springframework.stereotype..")))
            .as("les couches pures ne doivent utiliser de Spring que pour les stéréotypes de "
                    + "composants (org.springframework.stereotype) (ARCH-010)");
}
