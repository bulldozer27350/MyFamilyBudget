package com.moe.myfamilybudget.server.internal.architecture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.moe.myfamilybudget.server.internal.calculation.archfixture.EngineWithOpenApiDtoFixture;
import com.moe.myfamilybudget.server.internal.calculation.archfixture.PatrimoineWithObjectifsFixture;
import com.moe.myfamilybudget.server.internal.calculation.archfixture.PatrimoineWithTresorerieFixture;
import com.moe.myfamilybudget.server.internal.calculation.archfixture.RetirementWithAnalyseFixture;
import com.moe.myfamilybudget.server.internal.calculation.archfixture.RetirementWithFiscaliteFixture;
import com.moe.myfamilybudget.server.internal.calculation.archfixture.RetirementWithNotificationsFixture;
import com.moe.myfamilybudget.server.internal.calculation.archfixture.RetirementWithOverviewFixture;
import com.moe.myfamilybudget.server.internal.calculation.archfixture.RetirementWithRestFacadeFixture;
import com.moe.myfamilybudget.server.internal.calculation.archfixture.TaxWithRetirementInternalsFixture;
import com.moe.myfamilybudget.server.internal.calculation.archfixture.TaxWithRetirementProjectionFixture;
import com.moe.myfamilybudget.server.internal.calculation.archfixture.TaxWithTresorerieFixture;
import com.moe.myfamilybudget.server.internal.calculation.archfixture.TresorerieWithProjectionsFixture;
import com.moe.myfamilybudget.server.internal.calculation.archfixture.TresorerieWithRetirementInternalsFixture;

/**
 * ARCH-020, vérification négative : chaque règle de {@link DomainBoundaryRules} doit échouer sur une
 * fixture volontairement fautive et rester verte sur les fixtures conformes. Les fixtures résident
 * dans {@code internal.calculation.archfixture} (sources de test uniquement, exclues de
 * {@link DomainBoundaryArchTest} par {@code DoNotIncludeTests}).
 */
class DomainBoundaryRulesNegativeTest {

    private static JavaClasses classes(Class<?>... types) {
        return new ClassFileImporter().importClasses(types);
    }

    /**
     * Les fixtures conformes n'illustrent pas chaque domaine : une regle dont le {@code that()} ne retient aucune
     * classe est donc vide ici, ce qui ne constitue pas une violation ({@code allowEmptyShould}).
     */
    private static void assertCompliant(ArchRule rule, JavaClasses compliant) {
        assertDoesNotThrow(() -> rule.allowEmptyShould(true).check(compliant));
    }

    private static void assertFails(ArchRule rule, Class<?> fixture) {
        assertThrows(AssertionError.class, () -> rule.check(classes(fixture)));
    }

    @Test
    @DisplayName("Les fixtures conformes (projections publiées) ne violent aucune règle")
    void compliantFixturesViolateNothing() {
        JavaClasses compliant = classes(TaxWithRetirementProjectionFixture.class,
                TresorerieWithProjectionsFixture.class);

        assertCompliant(DomainBoundaryRules.RETRAITE_DOES_NOT_DEPEND_ON_OTHER_DOMAINS, compliant);
        assertCompliant(DomainBoundaryRules.FISCALITE_CONSUMES_RETIREMENT_PROJECTION_ONLY, compliant);
        assertCompliant(DomainBoundaryRules.FISCALITE_DOES_NOT_DEPEND_ON_TRESORERIE, compliant);
        assertCompliant(DomainBoundaryRules.TRESORERIE_CONSUMES_EXPLICIT_PROJECTIONS_ONLY, compliant);
        assertCompliant(DomainBoundaryRules.PATRIMOINE_DOES_NOT_DEPEND_ON_TRESORERIE, compliant);
        assertCompliant(DomainBoundaryRules.PATRIMOINE_DOES_NOT_DEPEND_ON_OBJECTIFS, compliant);
        assertCompliant(DomainBoundaryRules.ANALYSE_IS_A_CONSUMER_ONLY, compliant);
        assertCompliant(DomainBoundaryRules.OVERVIEW_IS_AN_AGGREGATOR_ONLY, compliant);
        assertCompliant(DomainBoundaryRules.NOTIFICATIONS_IS_A_CONSUMER_ONLY, compliant);
        assertCompliant(DomainBoundaryRules.DOMAIN_DOES_NOT_DEPEND_ON_REST_FACADE, compliant);
        assertCompliant(DomainBoundaryRules.DOMAIN_DOES_NOT_DEPEND_ON_OPENAPI_DTO, compliant);
    }

    @Test
    @DisplayName("RETRAITE_DOES_NOT_DEPEND_ON_OTHER_DOMAINS échoue sur Retraite -> Fiscalité")
    void retraiteRuleFails() {
        assertFails(DomainBoundaryRules.RETRAITE_DOES_NOT_DEPEND_ON_OTHER_DOMAINS,
                RetirementWithFiscaliteFixture.class);
    }

    @Test
    @DisplayName("FISCALITE_CONSUMES_RETIREMENT_PROJECTION_ONLY échoue sur Fiscalité -> moteur Retraite")
    void fiscaliteRetirementRuleFails() {
        assertFails(DomainBoundaryRules.FISCALITE_CONSUMES_RETIREMENT_PROJECTION_ONLY,
                TaxWithRetirementInternalsFixture.class);
    }

    @Test
    @DisplayName("FISCALITE_DOES_NOT_DEPEND_ON_TRESORERIE échoue sur Fiscalité -> Trésorerie")
    void fiscaliteTresorerieRuleFails() {
        assertFails(DomainBoundaryRules.FISCALITE_DOES_NOT_DEPEND_ON_TRESORERIE,
                TaxWithTresorerieFixture.class);
    }

    @Test
    @DisplayName("TRESORERIE_CONSUMES_EXPLICIT_PROJECTIONS_ONLY échoue sur Trésorerie -> moteur Retraite")
    void tresorerieRuleFails() {
        assertFails(DomainBoundaryRules.TRESORERIE_CONSUMES_EXPLICIT_PROJECTIONS_ONLY,
                TresorerieWithRetirementInternalsFixture.class);
    }

    @Test
    @DisplayName("PATRIMOINE_DOES_NOT_DEPEND_ON_TRESORERIE échoue sur Patrimoine -> Trésorerie")
    void patrimoineTresorerieRuleFails() {
        assertFails(DomainBoundaryRules.PATRIMOINE_DOES_NOT_DEPEND_ON_TRESORERIE,
                PatrimoineWithTresorerieFixture.class);
    }

    @Test
    @DisplayName("PATRIMOINE_DOES_NOT_DEPEND_ON_OBJECTIFS échoue sur Patrimoine -> Objectifs")
    void patrimoineObjectifsRuleFails() {
        assertFails(DomainBoundaryRules.PATRIMOINE_DOES_NOT_DEPEND_ON_OBJECTIFS,
                PatrimoineWithObjectifsFixture.class);
    }

    @Test
    @DisplayName("ANALYSE_IS_A_CONSUMER_ONLY échoue sur un domaine qui dépend d'Analyse")
    void analyseRuleFails() {
        assertFails(DomainBoundaryRules.ANALYSE_IS_A_CONSUMER_ONLY, RetirementWithAnalyseFixture.class);
    }

    @Test
    @DisplayName("OVERVIEW_IS_AN_AGGREGATOR_ONLY échoue sur un domaine qui dépend d'Overview")
    void overviewRuleFails() {
        assertFails(DomainBoundaryRules.OVERVIEW_IS_AN_AGGREGATOR_ONLY, RetirementWithOverviewFixture.class);
    }

    @Test
    @DisplayName("NOTIFICATIONS_IS_A_CONSUMER_ONLY échoue sur un domaine qui dépend de Notifications")
    void notificationsRuleFails() {
        assertFails(DomainBoundaryRules.NOTIFICATIONS_IS_A_CONSUMER_ONLY,
                RetirementWithNotificationsFixture.class);
    }

    @Test
    @DisplayName("DOMAIN_DOES_NOT_DEPEND_ON_REST_FACADE échoue sur un domaine qui dépend d'un service d'API")
    void restFacadeRuleFails() {
        assertFails(DomainBoundaryRules.DOMAIN_DOES_NOT_DEPEND_ON_REST_FACADE,
                RetirementWithRestFacadeFixture.class);
    }

    @Test
    @DisplayName("DOMAIN_DOES_NOT_DEPEND_ON_OPENAPI_DTO échoue sur un domaine qui dépend d'un DTO OpenAPI")
    void openApiRuleFails() {
        assertFails(DomainBoundaryRules.DOMAIN_DOES_NOT_DEPEND_ON_OPENAPI_DTO, EngineWithOpenApiDtoFixture.class);
    }
}
