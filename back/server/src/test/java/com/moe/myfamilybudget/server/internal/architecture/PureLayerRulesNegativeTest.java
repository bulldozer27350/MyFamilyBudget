package com.moe.myfamilybudget.server.internal.architecture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.moe.myfamilybudget.server.internal.calculation.archfixture.CompliantEngineFixture;
import com.moe.myfamilybudget.server.internal.calculation.archfixture.EngineWithBudgetDataModelFixture;
import com.moe.myfamilybudget.server.internal.calculation.archfixture.EngineWithJpaApiFixture;
import com.moe.myfamilybudget.server.internal.calculation.archfixture.EngineWithOpenApiDtoFixture;
import com.moe.myfamilybudget.server.internal.calculation.archfixture.EngineWithPersistenceEntityFixture;
import com.moe.myfamilybudget.server.internal.calculation.archfixture.EngineWithSpringWebFixture;

/**
 * ARCH-010, vérification négative : chaque règle de {@link PureLayerRules} doit échouer sur une
 * fixture volontairement fautive et rester verte sur une fixture conforme. Les fixtures résident
 * dans {@code internal.calculation.archfixture} (sources de test uniquement, exclues de
 * {@link PureLayerArchTest} par {@code DoNotIncludeTests}).
 */
class PureLayerRulesNegativeTest {

    private static JavaClasses classes(Class<?>... types) {
        return new ClassFileImporter().importClasses(types);
    }

    @Test
    @DisplayName("Une fixture conforme (stéréotype Spring autorisé) ne viole aucune règle")
    void compliantFixtureViolatesNothing() {
        JavaClasses compliant = classes(CompliantEngineFixture.class);

        assertDoesNotThrow(() -> PureLayerRules.NO_BUDGET_DATA_MODEL.check(compliant));
        assertDoesNotThrow(() -> PureLayerRules.NO_PERSISTENCE.check(compliant));
        assertDoesNotThrow(() -> PureLayerRules.NO_JPA_API.check(compliant));
        assertDoesNotThrow(() -> PureLayerRules.NO_OPENAPI_DTO.check(compliant));
        assertDoesNotThrow(() -> PureLayerRules.SPRING_LIMITED_TO_STEREOTYPES.check(compliant));
    }

    @Test
    @DisplayName("NO_BUDGET_DATA_MODEL échoue sur un moteur qui référence BudgetDataModel")
    void budgetDataModelRuleFails() {
        assertThrows(AssertionError.class,
                () -> PureLayerRules.NO_BUDGET_DATA_MODEL.check(classes(EngineWithBudgetDataModelFixture.class)));
    }

    @Test
    @DisplayName("NO_PERSISTENCE échoue sur un moteur qui référence une entité de persistance")
    void persistenceRuleFails() {
        assertThrows(AssertionError.class,
                () -> PureLayerRules.NO_PERSISTENCE.check(classes(EngineWithPersistenceEntityFixture.class)));
    }

    @Test
    @DisplayName("NO_JPA_API échoue sur un moteur qui référence l'API JPA")
    void jpaApiRuleFails() {
        assertThrows(AssertionError.class,
                () -> PureLayerRules.NO_JPA_API.check(classes(EngineWithJpaApiFixture.class)));
    }

    @Test
    @DisplayName("NO_OPENAPI_DTO échoue sur un moteur qui référence un DTO OpenAPI")
    void openApiRuleFails() {
        assertThrows(AssertionError.class,
                () -> PureLayerRules.NO_OPENAPI_DTO.check(classes(EngineWithOpenApiDtoFixture.class)));
    }

    @Test
    @DisplayName("SPRING_LIMITED_TO_STEREOTYPES échoue sur un moteur qui utilise Spring hors stéréotypes")
    void springRuleFails() {
        assertThrows(AssertionError.class,
                () -> PureLayerRules.SPRING_LIMITED_TO_STEREOTYPES.check(classes(EngineWithSpringWebFixture.class)));
    }
}
