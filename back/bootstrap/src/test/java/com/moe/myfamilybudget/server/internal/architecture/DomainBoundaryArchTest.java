package com.moe.myfamilybudget.server.internal.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * ARCH-020 : applique sur le code de production les interdictions de dépendances inter-domaines
 * définies par {@link DomainBoundaryRules}. Règles strictes, sans gel : toute nouvelle violation
 * fait échouer le build.
 */
@AnalyzeClasses(packages = "com.moe.myfamilybudget", importOptions = ImportOption.DoNotIncludeTests.class)
class DomainBoundaryArchTest {

    @ArchTest
    static final ArchRule RETRAITE_DOES_NOT_DEPEND_ON_OTHER_DOMAINS =
            DomainBoundaryRules.RETRAITE_DOES_NOT_DEPEND_ON_OTHER_DOMAINS;

    @ArchTest
    static final ArchRule FISCALITE_CONSUMES_RETIREMENT_PROJECTION_ONLY =
            DomainBoundaryRules.FISCALITE_CONSUMES_RETIREMENT_PROJECTION_ONLY;

    @ArchTest
    static final ArchRule FISCALITE_DOES_NOT_DEPEND_ON_TRESORERIE =
            DomainBoundaryRules.FISCALITE_DOES_NOT_DEPEND_ON_TRESORERIE;

    @ArchTest
    static final ArchRule TRESORERIE_CONSUMES_EXPLICIT_PROJECTIONS_ONLY =
            DomainBoundaryRules.TRESORERIE_CONSUMES_EXPLICIT_PROJECTIONS_ONLY;

    @ArchTest
    static final ArchRule PATRIMOINE_DOES_NOT_DEPEND_ON_TRESORERIE =
            DomainBoundaryRules.PATRIMOINE_DOES_NOT_DEPEND_ON_TRESORERIE;

    @ArchTest
    static final ArchRule PATRIMOINE_DOES_NOT_DEPEND_ON_OBJECTIFS =
            DomainBoundaryRules.PATRIMOINE_DOES_NOT_DEPEND_ON_OBJECTIFS;

    @ArchTest
    static final ArchRule ANALYSE_IS_A_CONSUMER_ONLY = DomainBoundaryRules.ANALYSE_IS_A_CONSUMER_ONLY;

    @ArchTest
    static final ArchRule OVERVIEW_IS_AN_AGGREGATOR_ONLY = DomainBoundaryRules.OVERVIEW_IS_AN_AGGREGATOR_ONLY;

    @ArchTest
    static final ArchRule NOTIFICATIONS_IS_A_CONSUMER_ONLY = DomainBoundaryRules.NOTIFICATIONS_IS_A_CONSUMER_ONLY;

    @ArchTest
    static final ArchRule DOMAIN_DOES_NOT_DEPEND_ON_REST_FACADE =
            DomainBoundaryRules.DOMAIN_DOES_NOT_DEPEND_ON_REST_FACADE;

    @ArchTest
    static final ArchRule DOMAIN_DOES_NOT_DEPEND_ON_OPENAPI_DTO =
            DomainBoundaryRules.DOMAIN_DOES_NOT_DEPEND_ON_OPENAPI_DTO;
}
