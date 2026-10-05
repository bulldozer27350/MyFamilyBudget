package com.moe.myfamilybudget.server.internal.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.regex.Pattern;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Règles ARCH-020 (voir doc/architecture/19-backlog-pre-maven-patchs.md) : interdictions de
 * dépendances inter-domaines, qui codent le graphe cible de {@code 00-principes.md} avant la
 * création des modules Maven.
 *
 * <p>Les packages {@code internal.calculation} et {@code internal.model} sont communs à tous les
 * domaines : les domaines sont donc reconnus par le nom de leur classe de premier niveau (les
 * classes imbriquées sont rattachées à leur classe englobante). Les règles sont définies ici pour
 * être exécutées par {@link DomainBoundaryArchTest} sur le code de production et par
 * {@link DomainBoundaryRulesNegativeTest} sur des fixtures volontairement fautives.
 *
 * <p>Principes de lecture :
 * <ul>
 *   <li>un domaine peut consommer la <b>projection</b> d'un autre domaine (un contrat de données,
 *       par exemple {@code TaxProjection}, {@code CashflowProjection}), jamais son
 *       <b>implémentation</b> (moteur, entrée de calcul, paramètres, modèle de résultat) ;</li>
 *   <li>les règles « domaine vers domaine » visent les couches pures
 *       ({@link PureLayerRules#PURE_LAYER}) : {@code internal.factory} n'est pas visé, les
 *       assemblers peuvent lire plusieurs domaines pendant la transition ;</li>
 *   <li>les règles « consommateurs » et « couche REST » visent l'ensemble des couches de domaine
 *       (calcul, modèle, notification, factory, port, command, persistence, updater, marketdata,
 *       enablebanking) ;</li>
 *   <li>les packages Maven finaux ne sont pas imposés : seules les frontières logiques existantes
 *       sont protégées.</li>
 * </ul>
 */
final class DomainBoundaryRules {

    private DomainBoundaryRules() {
    }

    /** Nom de la classe de premier niveau (sans paquet ni classe imbriquée). */
    private static String topLevelSimpleName(JavaClass javaClass) {
        String name = javaClass.getName();
        String simple = name.substring(name.lastIndexOf('.') + 1);
        int nested = simple.indexOf('$');
        return nested < 0 ? simple : simple.substring(0, nested);
    }

    static DescribedPredicate<JavaClass> topLevelNameMatching(String description, String regex) {
        Pattern pattern = Pattern.compile(regex);
        return new DescribedPredicate<JavaClass>(description) {
            @Override
            public boolean test(JavaClass javaClass) {
                return pattern.matcher(topLevelSimpleName(javaClass)).matches();
            }
        };
    }

    // ------------------------------------------------------------------------------------------
    // Domaines : membres (origine des règles) et implémentations internes (cibles interdites)
    // ------------------------------------------------------------------------------------------

    /** Membres du domaine Retraite, y compris ses projections publiées. */
    static final DescribedPredicate<JavaClass> RETRAITE = topLevelNameMatching("le domaine Retraite",
            "Retirement\\w*|Retraite\\w*|AnnualSalaryProjection|SalaryHistoryEntry|PauseState");

    /** Implémentation Retraite : ce qui ne doit pas être consommé par les autres domaines. */
    static final DescribedPredicate<JavaClass> RETRAITE_INTERNALS = topLevelNameMatching(
            "les internals Retraite (moteur, entrées, paramètres, résultats)",
            "RetirementCalculationService|RetirementCalculationInput|RetirementPersonInput"
                    + "|RetirementParameters|RetraiteResultModel|RetraitePersonWithProjectionModel");

    /** Membres du domaine Fiscalité, y compris sa projection {@code TaxProjection}. */
    static final DescribedPredicate<JavaClass> FISCALITE = topLevelNameMatching("le domaine Fiscalité",
            "Tax\\w*|AnnualTaxIncome|AnnualVariableIncome");

    /** Implémentation Fiscalité : tout sauf la projection {@code TaxProjection}. */
    static final DescribedPredicate<JavaClass> FISCALITE_INTERNALS = topLevelNameMatching(
            "les internals Fiscalité (moteur, entrées, paramètres, résultats)",
            "Tax(?!Projection$)\\w*|AnnualTaxIncome|AnnualVariableIncome");

    /** Domaine Trésorerie : moteur, entrées, paramètres, projection et résultats. */
    static final DescribedPredicate<JavaClass> TRESORERIE = topLevelNameMatching("le domaine Trésorerie",
            "Tresorerie\\w*|Treasury\\w*");

    /** Membres du domaine Patrimoine (hors données de placement partagées avec le budget). */
    static final DescribedPredicate<JavaClass> PATRIMOINE = topLevelNameMatching("le domaine Patrimoine",
            "Patrimoine\\w*|PlacementEvolution\\w*|PlacementProjectionInput|PlacementRateSuggestion\\w*"
                    + "|ContributionPauseRules");

    /**
     * Implémentation Patrimoine : moteurs, entrées et paramètres. Les projections publiées
     * ({@code PlacementBalanceSnapshot}) n'en font pas partie.
     */
    static final DescribedPredicate<JavaClass> PATRIMOINE_INTERNALS = topLevelNameMatching(
            "les internals Patrimoine (moteurs, entrées, paramètres)",
            "PatrimoineProjectionService|PatrimoineProjectionInput|PatrimoineProjectionParameters"
                    + "|PlacementEvolution\\w*|PlacementProjectionInput|PlacementRateSuggestion\\w*");

    /** Domaine Objectifs. */
    static final DescribedPredicate<JavaClass> OBJECTIFS = topLevelNameMatching("le domaine Objectifs",
            "Objectif\\w*");

    /** Domaine Analyse (consommateur final). */
    static final DescribedPredicate<JavaClass> ANALYSE = topLevelNameMatching("le domaine Analyse",
            "Analyse\\w*|AnalysisPeriod");

    /** Domaine Overview (agrégateur final). */
    static final DescribedPredicate<JavaClass> OVERVIEW = topLevelNameMatching("le domaine Overview",
            "Overview\\w*");

    // ------------------------------------------------------------------------------------------
    // Couches
    // ------------------------------------------------------------------------------------------

    /** Toutes les couches de domaine : tout {@code internal} sauf la façade applicative. */
    static final DescribedPredicate<JavaClass> DOMAIN_LAYERS = resideInAnyPackage(
            "..internal.calculation..", "..internal.model..",
            "com.moe.myfamilybudget.domain.retirement..",
            "com.moe.myfamilybudget.domain.tax..",
            "com.moe.myfamilybudget.domain.wealth..",
            "com.moe.myfamilybudget.domain.bankpointage..",
            "com.moe.myfamilybudget.domain.treasury..",
            "com.moe.myfamilybudget.domain.analysis..",
            "com.moe.myfamilybudget.domain.credit..",
            "com.moe.myfamilybudget.domain.goals..",
            "com.moe.myfamilybudget.domain.notifications..",
            "com.moe.myfamilybudget.domain.market..",
            "..internal.notification..",
            "..internal.factory..", "..internal.port..", "..internal.command..",
            "com.moe.myfamilybudget.application.command..", "com.moe.myfamilybudget.application.factory..",
            "com.moe.myfamilybudget.application.settings..", "com.moe.myfamilybudget.application.model..",
            "com.moe.myfamilybudget.application.overview..",
            "com.moe.myfamilybudget.transition..",
            "com.moe.myfamilybudget.persistence..", "..internal.marketdata..",
            "..internal.enablebanking..")
            .as("les couches de domaine (calcul, modèle, notification, factory, port, command, "
                    + "persistence (module persistence, updater inclus), marketdata, enablebanking)");

    /** Façade applicative REST : implémentations d'API, mappers, snapshot global, contrôleurs. */
    private static final DescribedPredicate<JavaClass> REST_FACADE = resideInAnyPackage(
            "..internal.impl..", "..internal.mapper..", "..internal.controller..", "com.moe.myfamilybudget.application.snapshot..",
            "com.moe.myfamilybudget.application.service..", "com.moe.myfamilybudget.application.mapper..")
            .as("la façade applicative REST (internal.impl, mapper, controller, snapshot, application.service, "
                    + "application.mapper)");

    private static final DescribedPredicate<JavaClass> OPENAPI_DTO = resideInAnyPackage(
            "com.moe.myfamilybudget.api..", "com.moe.myfamilybudget.server.api..")
            .as("les DTO et contrats OpenAPI (com.moe.myfamilybudget.api)");

    private static final DescribedPredicate<JavaClass> NOTIFICATIONS_FAMILY =
            resideInAPackage("..internal.notification..")
                    .or(resideInAPackage("com.moe.myfamilybudget.domain.notifications.."))
                    .or(topLevelNameMatching("NotificationInputFactory", "NotificationInputFactory"))
                    .as("le domaine Notifications");

    // ------------------------------------------------------------------------------------------
    // Retraite, Fiscalité, Trésorerie
    // ------------------------------------------------------------------------------------------

    static final ArchRule RETRAITE_DOES_NOT_DEPEND_ON_OTHER_DOMAINS = noClasses()
            .that(PureLayerRules.PURE_LAYER).and(RETRAITE)
            .should().dependOnClassesThat(FISCALITE_INTERNALS
                    .or(TRESORERIE).or(PATRIMOINE_INTERNALS).or(OBJECTIFS))
            .as("Retraite ne dépend pas des internals Fiscalité, Trésorerie, Patrimoine ni Objectifs (ARCH-020)");

    static final ArchRule FISCALITE_CONSUMES_RETIREMENT_PROJECTION_ONLY = noClasses()
            .that(PureLayerRules.PURE_LAYER).and(FISCALITE)
            .should().dependOnClassesThat(RETRAITE_INTERNALS)
            .as("Fiscalité porte son propre contrat de pension (TaxablePensionIncome) et ne dépend pas "
                    + "de l'implémentation Retraite (ARCH-020, SILO-130)");

    static final ArchRule FISCALITE_DOES_NOT_DEPEND_ON_TRESORERIE = noClasses()
            .that(PureLayerRules.PURE_LAYER).and(FISCALITE)
            .should().dependOnClassesThat(TRESORERIE)
            .as("Fiscalité ne dépend pas de Trésorerie : pas de cycle Fiscalité <-> Trésorerie (ARCH-020)");

    static final ArchRule TRESORERIE_CONSUMES_EXPLICIT_PROJECTIONS_ONLY = noClasses()
            .that(PureLayerRules.PURE_LAYER).and(TRESORERIE)
            .should().dependOnClassesThat(RETRAITE_INTERNALS.or(FISCALITE_INTERNALS).or(PATRIMOINE_INTERNALS))
            .as("Trésorerie porte ses propres contrats d'entrée (TreasuryPensionProjection, "
                    + "TreasuryTaxProjection, TreasuryPlacementCashflow) et ne dépend pas des internals "
                    + "Retraite, Fiscalité ni Patrimoine (ARCH-020, SILO-132)");

    // ------------------------------------------------------------------------------------------
    // Patrimoine
    // ------------------------------------------------------------------------------------------

    static final ArchRule PATRIMOINE_DOES_NOT_DEPEND_ON_TRESORERIE = noClasses()
            .that(PureLayerRules.PURE_LAYER).and(PATRIMOINE)
            .should().dependOnClassesThat(TRESORERIE)
            .as("Patrimoine ne dépend pas des internals Trésorerie : pas de cycle "
                    + "Patrimoine <-> Trésorerie (ARCH-020)");

    static final ArchRule PATRIMOINE_DOES_NOT_DEPEND_ON_OBJECTIFS = noClasses()
            .that(PureLayerRules.PURE_LAYER).and(PATRIMOINE)
            .should().dependOnClassesThat(OBJECTIFS)
            .as("Patrimoine ne dépend pas d'Objectifs : pas de cycle Objectifs <-> Patrimoine (ARCH-020)");

    // ------------------------------------------------------------------------------------------
    // Consommateurs / agrégateurs : Analyse, Overview, Notifications
    // ------------------------------------------------------------------------------------------

    static final ArchRule ANALYSE_IS_A_CONSUMER_ONLY = noClasses()
            .that(DOMAIN_LAYERS).and(new NotMatching(ANALYSE))
            .should().dependOnClassesThat(ANALYSE)
            .as("aucun autre domaine ne dépend d'Analyse, consommateur final (ARCH-020)");

    static final ArchRule OVERVIEW_IS_AN_AGGREGATOR_ONLY = noClasses()
            .that(DOMAIN_LAYERS).and(new NotMatching(OVERVIEW))
            .should().dependOnClassesThat(OVERVIEW)
            .as("aucun autre domaine ne dépend d'Overview, agrégateur final (ARCH-020)");

    static final ArchRule NOTIFICATIONS_IS_A_CONSUMER_ONLY = noClasses()
            .that(DOMAIN_LAYERS).and(new NotMatching(NOTIFICATIONS_FAMILY))
            .should().dependOnClassesThat(NOTIFICATIONS_FAMILY)
            .as("aucun autre domaine ne dépend de Notifications, consommateur final (ARCH-020)");

    // ------------------------------------------------------------------------------------------
    // REST / OpenAPI
    // ------------------------------------------------------------------------------------------

    static final ArchRule DOMAIN_DOES_NOT_DEPEND_ON_REST_FACADE = noClasses()
            .that(DOMAIN_LAYERS)
            .should().dependOnClassesThat(REST_FACADE)
            .as("aucun domaine ne dépend d'un contrôleur REST, d'une implémentation d'API, "
                    + "d'un mapper ni du snapshot applicatif (ARCH-020)");

    static final ArchRule DOMAIN_DOES_NOT_DEPEND_ON_OPENAPI_DTO = noClasses()
            .that(DOMAIN_LAYERS)
            .should().dependOnClassesThat(OPENAPI_DTO)
            .as("aucun domaine ne dépend d'un DTO ni d'un contrat OpenAPI (ARCH-020)");

    /** Négation d'un prédicat de classe (utilitaire local, sans effet sur la description). */
    private static final class NotMatching extends DescribedPredicate<JavaClass> {
        private final DescribedPredicate<JavaClass> delegate;

        NotMatching(DescribedPredicate<JavaClass> delegate) {
            super("hors de " + delegate.getDescription());
            this.delegate = delegate;
        }

        @Override
        public boolean test(JavaClass javaClass) {
            return !delegate.test(javaClass);
        }
    }
}
