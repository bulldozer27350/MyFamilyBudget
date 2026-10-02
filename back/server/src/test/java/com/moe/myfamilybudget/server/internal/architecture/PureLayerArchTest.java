package com.moe.myfamilybudget.server.internal.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * ARCH-010 : applique sur le code de production les interdictions des couches pures définies par
 * {@link PureLayerRules}. Règles strictes, sans gel : toute nouvelle violation fait échouer le
 * build. Les règles historiques de {@link CalculationDependenciesArchTest} (par moteur) restent
 * en place.
 */
@AnalyzeClasses(packages = "com.moe.myfamilybudget", importOptions = ImportOption.DoNotIncludeTests.class)
class PureLayerArchTest {

    @ArchTest
    static final ArchRule NO_BUDGET_DATA_MODEL = PureLayerRules.NO_BUDGET_DATA_MODEL;

    @ArchTest
    static final ArchRule NO_PERSISTENCE = PureLayerRules.NO_PERSISTENCE;

    @ArchTest
    static final ArchRule NO_JPA_API = PureLayerRules.NO_JPA_API;

    @ArchTest
    static final ArchRule NO_OPENAPI_DTO = PureLayerRules.NO_OPENAPI_DTO;

    @ArchTest
    static final ArchRule SPRING_LIMITED_TO_STEREOTYPES = PureLayerRules.SPRING_LIMITED_TO_STEREOTYPES;
}
