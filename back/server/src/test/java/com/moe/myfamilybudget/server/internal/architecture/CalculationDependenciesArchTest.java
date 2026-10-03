package com.moe.myfamilybudget.server.internal.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.moe.myfamilybudget.server.internal.calculation.LoanAdviceCalculationService;
import com.moe.myfamilybudget.server.internal.calculation.OverviewCalculationService;
import com.moe.myfamilybudget.domain.wealth.calculation.PatrimoineProjectionService;
import com.moe.myfamilybudget.domain.wealth.calculation.PlacementEvolutionService;
import com.moe.myfamilybudget.server.internal.calculation.PlacementRateSuggestionService;
import com.moe.myfamilybudget.server.internal.calculation.TresorerieCalculationService;
import com.moe.myfamilybudget.server.internal.factory.AssetBucketResolver;
import com.moe.myfamilybudget.server.internal.model.AnalyseCalculator;
import com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.domain.budget.ChargeModel;
import com.moe.myfamilybudget.domain.budget.IncomeModel;
import com.moe.myfamilybudget.server.internal.model.LoanModel;
import com.moe.myfamilybudget.server.internal.model.ObjectifAllocationModel;
import com.moe.myfamilybudget.server.internal.model.ObjectifModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementHistoryEntryModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.domain.bankpointage.calculation.PointageCalculator;
import com.moe.myfamilybudget.server.internal.model.PointageModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;
import com.moe.myfamilybudget.domain.tax.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.domain.tax.model.TaxBracketModel;
import com.moe.myfamilybudget.domain.tax.calculation.TaxCalculator;
import com.moe.myfamilybudget.domain.tax.model.TaxChildModel;
import com.moe.myfamilybudget.domain.tax.model.TaxRateOverrideModel;
import com.moe.myfamilybudget.domain.budget.VariableIncomeModel;
import com.moe.myfamilybudget.domain.budget.VariableOverrideModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

/**
 * Garde-fou d'architecture RF-001 (voir doc/architecture/00-principes.md, section
 * « Ce qu'il ne faut surtout pas faire » : « Faire dépendre un module métier de la
 * persistance ») et doc/architecture/01-sequencement.md, étape 1.
 *
 * <p>Règle : le package {@code internal.calculation} — les moteurs de calcul — ne doit pas
 * dépendre de {@link BudgetDataModel}. Un moteur de calcul reçoit un {@code XxxInput} dédié,
 * jamais le modèle persistant complet ; c'est le signal d'alerte central de tout le chantier de
 * découplage (test de conception à 4 questions, voir 00-principes.md).
 *
 * <p>Les factories de composition ({@code RetirementInputFactory}, {@code TaxInputFactory}...)
 * vivent dans {@code internal.factory}, hors du périmètre de cette règle : elles sont le lieu
 * normal où {@code BudgetDataModel} est traduit en {@code XxxInput}. Le domaine Retraite n'a plus
 * aucune violation depuis RF-103 ; le domaine Fiscalité n'en a plus depuis RF-203 (aucune
 * violation Fiscalité n'était présente dans le store gelé : le moteur fiscal vit dans
 * {@code internal.model}, hors du périmètre de la règle gelée). Il est désormais protégé par la
 * règle dédiée {@link #TAX_ENGINE_DOES_NOT_DEPEND_ON_PERSISTENT_MODELS}, non gelée. Même situation
 * pour le domaine Pointage depuis RF-502 : aucune violation Pointage dans le store gelé (le moteur
 * vit dans {@code internal.model}), désormais protégé par
 * {@link #POINTAGE_ENGINE_DOES_NOT_DEPEND_ON_BUDGET_MODELS}, non gelée. Et pour le domaine
 * Notifications depuis RF-703 : les règles ({@code internal.notification.rules}) n'avaient plus
 * de dépendance au budget depuis RF-702, le domaine est protégé par
 * {@link #NOTIFICATION_RULES_DO_NOT_DEPEND_ON_BUDGET_MODELS}, non gelée (le
 * {@code NotificationDispatchService}, qui assemble les entrées, n'est pas concerné : sa lecture du
 * budget relève des ports de lecture RF-B00/RF-B01). Et pour le domaine Crédit depuis RF-803 : le
 * store gelé ne contient aucune violation Crédit (les deux moteurs, {@code LoanAdviceCalculationService}
 * et {@code PlacementRateSuggestionService}, ne dépendent plus du budget depuis RF-801/RF-802) ; ils
 * sont protégés par {@link #CREDIT_ENGINES_DO_NOT_DEPEND_ON_BUDGET_MODELS}, non gelée. Et pour le
 * domaine Patrimoine depuis RF-302 : le store gelé ne contient aucune violation Patrimoine (les deux
 * moteurs, {@code PatrimoineProjectionService} et {@code PlacementEvolutionService}, ne dépendent
 * plus du budget depuis RF-301) ; ils sont protégés par
 * {@link #PATRIMOINE_ENGINES_DO_NOT_DEPEND_ON_BUDGET_MODELS}, non gelée. Et pour le
 * domaine Trésorerie depuis RF-402 : le store gelé ne contient aucune violation Trésorerie (le
 * moteur, {@code TresorerieCalculationService}, ne dépend plus du budget depuis RF-401) ; il est
 * protégé par {@link #TREASURY_ENGINE_DOES_NOT_DEPEND_ON_BUDGET_MODELS}, non gelée. Et pour le
 * domaine Overview depuis RF-901 : {@code OverviewCalculationService} consomme exclusivement
 * {@code OverviewInput} depuis RF-901 ; il est protégé par
 * {@link #OVERVIEW_ENGINE_DOES_NOT_DEPEND_ON_BUDGET_MODELS}, non gelée (RF-902). Et pour le
 * domaine Analyse depuis RF-601 : {@code AnalyseCalculator} consomme exclusivement
 * {@code AnalyseInput} depuis RF-601 ; il est protégé par
 * {@link #ANALYSE_ENGINE_DOES_NOT_DEPEND_ON_BUDGET_MODELS}, non gelée (RF-602).
 *
 * <p><b>Règle durcie (RF-902).</b> Toutes les violations préexistantes ont été résorbées domaine
 * par domaine (RF-103 à RF-902). La règle {@code FreezingArchRule} a été remplacée par une règle
 * stricte {@code noClasses()...} sans gel : le store {@code archunit_store} est vide (seul
 * {@code .gitkeep} subsiste). Toute nouvelle violation dans {@code internal.calculation} fait
 * maintenant échouer le build immédiatement.
 */
@AnalyzeClasses(packages = "com.moe.myfamilybudget", importOptions = ImportOption.DoNotIncludeTests.class)
class CalculationDependenciesArchTest {

    @ArchTest
    static final ArchRule CALCULATION_DOES_NOT_DEPEND_ON_BUDGET_DATA_MODEL = noClasses()
            .that().resideInAPackage("..internal.calculation..")
            .should().dependOnClassesThat().areAssignableTo(BudgetDataModel.class)
            .as("le package internal.calculation ne doit pas dépendre de BudgetDataModel "
                    + "(doc/architecture/00-principes.md) ; règle durcie en RF-902 : "
                    + "plus aucune violation n'existe depuis RF-901, voir la javadoc de cette classe");

    /**
     * Garde-fou du domaine Fiscalité (RF-203) : {@link TaxCalculator} ne reçoit que
     * {@code TaxCalculationInput} et ne connaît aucun modèle persistant. Règle stricte (sans gel) :
     * le domaine ne présente aucune violation préexistante.
     */
    @ArchTest
    static final ArchRule TAX_ENGINE_DOES_NOT_DEPEND_ON_PERSISTENT_MODELS = noClasses()
            .that().areAssignableTo(TaxCalculator.class)
            .should().dependOnClassesThat().belongToAnyOf(
                    BudgetDataModel.class,
                    SettingsModel.class,
                    IncomeModel.class,
                    VariableIncomeModel.class,
                    VariableOverrideModel.class,
                    TaxChildModel.class,
                    TaxBracketModel.class,
                    TaxRateOverrideModel.class,
                    TaxActualOverrideModel.class)
            .as("TaxCalculator ne doit dépendre d'aucun modèle persistant : il consomme uniquement "
                    + "TaxCalculationInput (doc/architecture/04-domaine-fiscalite.md)");

    /**
     * Garde-fou du domaine Pointage (RF-502) : {@link PointageCalculator} ne reçoit que
     * {@code PointageInput} (et les types de {@code BankImportModel}, calculateur déjà pur) ; il ne
     * connaît ni le budget, ni les charges, revenus, placements, paramètres, ni le modèle de lecture
     * {@link PointageModel} de {@code GET /pointage}. Règle stricte (sans gel) : le domaine ne
     * présente aucune violation préexistante.
     */
    @ArchTest
    static final ArchRule POINTAGE_ENGINE_DOES_NOT_DEPEND_ON_BUDGET_MODELS = noClasses()
            .that().areAssignableTo(PointageCalculator.class)
            .should().dependOnClassesThat().belongToAnyOf(
                    BudgetDataModel.class,
                    ChargeModel.class,
                    IncomeModel.class,
                    PlacementModel.class,
                    SettingsModel.class,
                    PointageModel.class)
            .as("PointageCalculator ne doit dépendre d'aucun modèle du budget : il consomme uniquement "
                    + "PointageInput (doc/architecture/07-domaine-banque-pointage.md)");

    /**
     * Garde-fou du domaine Notifications (RF-703) : les règles ne reçoivent que leur entrée dédiée
     * ({@code DebitThresholdInput}, {@code BalanceFloorInput}, {@code ObjectifReachableInput}) et ne
     * connaissent ni le budget ni ses sous-modèles. Règle stricte (sans gel) : le domaine ne
     * présente aucune violation préexistante.
     */
    @ArchTest
    static final ArchRule NOTIFICATION_RULES_DO_NOT_DEPEND_ON_BUDGET_MODELS = noClasses()
            .that().resideInAPackage("..internal.notification.rules..")
            .should().dependOnClassesThat().belongToAnyOf(
                    BudgetDataModel.class,
                    BankImportModel.class,
                    ObjectifModel.class,
                    ObjectifAllocationModel.class,
                    PlacementModel.class,
                    SettingsModel.class)
            .as("les règles de notification ne doivent dépendre d'aucun modèle du budget : elles "
                    + "consomment uniquement leur Input dédié "
                    + "(doc/architecture/09-domaine-objectifs-notifications.md)");

    /**
     * Garde-fou du service applicatif de notifications (NOTIF-010) : le package {@code internal.notification}
     * (dont {@code NotificationDispatchService}) lit le budget uniquement via les ports de lecture
     * ({@code internal.port}) et ne dépend ni de {@link PersistenceManager} ni de {@link BudgetDataModel}.
     * La règle ne vise volontairement pas {@code internal.factory} : les factories d'assemblage peuvent
     * légitimement manipuler les modèles pendant la transition. Règle stricte (sans gel).
     */
    @ArchTest
    static final ArchRule NOTIFICATION_PACKAGE_DOES_NOT_DEPEND_ON_PERSISTENCE_MANAGER = noClasses()
            .that().resideInAPackage("..internal.notification..")
            .should().dependOnClassesThat().belongToAnyOf(
                    PersistenceManager.class,
                    BudgetDataModel.class)
            .as("le package internal.notification doit lire le budget via les ports de lecture "
                    + "(internal.port), sans PersistenceManager ni BudgetDataModel "
                    + "(doc/architecture/09-domaine-objectifs-notifications.md)");

    /**
     * Garde-fou du domaine Crédit (RF-803) : les moteurs d'analyse des prêts et de suggestions de
     * taux ne reçoivent que {@code LoanAdviceInput} et {@code PlacementRateSuggestionInput} ; ils ne
     * connaissent ni le budget, ni les prêts, placements et catégories d'actifs, ni le résolveur de
     * bucket (outil d'assemblage de {@code internal.factory}, voir RF-802). Règle stricte (sans gel) :
     * le domaine ne présente aucune violation préexistante.
     */
    @ArchTest
    static final ArchRule CREDIT_ENGINES_DO_NOT_DEPEND_ON_BUDGET_MODELS = noClasses()
            .that().areAssignableTo(LoanAdviceCalculationService.class)
            .or().areAssignableTo(PlacementRateSuggestionService.class)
            .should().dependOnClassesThat().belongToAnyOf(
                    BudgetDataModel.class,
                    LoanModel.class,
                    PlacementModel.class,
                    AssetCategoryModel.class,
                    SettingsModel.class,
                    AssetBucketResolver.class)
            .as("les moteurs Crédit ne doivent dépendre d'aucun modèle du budget : ils consomment "
                    + "uniquement LoanAdviceInput et PlacementRateSuggestionInput "
                    + "(doc/architecture/10-domaine-prets-suggestions.md)");

    /**
     * Garde-fou du domaine Patrimoine (RF-302) : les deux moteurs patrimoniaux ne reçoivent que
     * {@code PatrimoineProjectionInput} et {@code PlacementEvolutionInput} ; ils ne connaissent ni
     * le budget, ni les placements, ni leur historique de valorisation. Règle stricte (sans gel) :
     * le domaine ne présente aucune violation préexistante.
     */
    @ArchTest
    static final ArchRule PATRIMOINE_ENGINES_DO_NOT_DEPEND_ON_BUDGET_MODELS = noClasses()
            .that().areAssignableTo(PatrimoineProjectionService.class)
            .or().areAssignableTo(PlacementEvolutionService.class)
            .should().dependOnClassesThat().belongToAnyOf(
                    BudgetDataModel.class,
                    PlacementModel.class,
                    PlacementHistoryEntryModel.class,
                    SettingsModel.class)
            .as("les moteurs patrimoniaux ne doivent dépendre d'aucun modèle du budget : ils "
                    + "consomment uniquement PatrimoineProjectionInput et PlacementEvolutionInput "
                    + "(doc/architecture/05-domaine-patrimoine.md)");

    /**
     * Garde-fou du domaine Trésorerie (RF-402) : {@link TresorerieCalculationService} ne reçoit que
     * {@code TreasuryProjectionInput} et ne connaît aucun modèle persistant du budget. Règle stricte
     * (sans gel) : le domaine ne présente aucune violation préexistante depuis RF-401.
     */
    @ArchTest
    static final ArchRule TREASURY_ENGINE_DOES_NOT_DEPEND_ON_BUDGET_MODELS = noClasses()
            .that().areAssignableTo(TresorerieCalculationService.class)
            .should().dependOnClassesThat().belongToAnyOf(
                    BudgetDataModel.class,
                    SettingsModel.class,
                    IncomeModel.class,
                    ChargeModel.class,
                    PlacementModel.class,
                    VariableIncomeModel.class,
                    VariableOverrideModel.class,
                    BankImportModel.class)
            .as("TresorerieCalculationService ne doit dépendre d'aucun modèle du budget : il "
                    + "consomme uniquement TreasuryProjectionInput "
                    + "(doc/architecture/06-domaine-tresorerie.md)");

    /**
     * Garde-fou du domaine Overview (RF-902) : {@link OverviewCalculationService} ne reçoit que
     * {@code OverviewInput} et ne connaît aucun modèle persistant du budget. Règle stricte
     * (sans gel) : le domaine ne présente aucune violation préexistante depuis RF-901.
     */
    @ArchTest
    static final ArchRule OVERVIEW_ENGINE_DOES_NOT_DEPEND_ON_BUDGET_MODELS = noClasses()
            .that().areAssignableTo(OverviewCalculationService.class)
            .should().dependOnClassesThat().belongToAnyOf(
                    BudgetDataModel.class,
                    SettingsModel.class,
                    IncomeModel.class,
                    ChargeModel.class,
                    PlacementModel.class,
                    VariableIncomeModel.class,
                    VariableOverrideModel.class)
            .as("OverviewCalculationService ne doit dépendre d'aucun modèle du budget : il "
                    + "consomme uniquement OverviewInput "
                    + "(doc/architecture/11-domaine-overview.md)");

    /**
     * Garde-fou du domaine Analyse (RF-602) : {@link AnalyseCalculator} ne reçoit que
     * {@code AnalyseInput} (et les types de {@code BankImportModel}, calculateur déjà pur) ; il ne
     * connaît ni le budget, ni les charges, revenus, placements, paramètres, ni {@code BudgetDataModel}.
     * Règle stricte (sans gel) : le domaine ne présente aucune violation préexistante depuis RF-601.
     */
    @ArchTest
    static final ArchRule ANALYSE_ENGINE_DOES_NOT_DEPEND_ON_BUDGET_MODELS = noClasses()
            .that().areAssignableTo(AnalyseCalculator.class)
            .should().dependOnClassesThat().belongToAnyOf(
                    BudgetDataModel.class,
                    SettingsModel.class,
                    IncomeModel.class,
                    ChargeModel.class,
                    PlacementModel.class,
                    VariableIncomeModel.class,
                    VariableOverrideModel.class)
            .as("AnalyseCalculator ne doit dépendre d'aucun modèle du budget : il consomme uniquement "
                    + "AnalyseInput (doc/architecture/08-domaine-analyse.md)");

    /**
     * Garde-fou d'architecture VT-400 : Les moteurs de calcul et couches pures du domaine ne doivent pas
     * dépendre de {@link PersistenceManager}.
     */
    @ArchTest
    static final ArchRule ENGINES_DO_NOT_DEPEND_ON_PERSISTENCE_MANAGER = noClasses()
            .that().resideInAPackage("..internal.calculation..")
            .or().resideInAPackage("..internal.model..")
            .or().resideInAPackage("com.moe.myfamilybudget.domain.budget..")
            .or().resideInAPackage("com.moe.myfamilybudget.domain.retirement..")
            .or().resideInAPackage("com.moe.myfamilybudget.domain.tax..")
            .or().resideInAPackage("com.moe.myfamilybudget.domain.wealth..")
            .or().resideInAPackage("com.moe.myfamilybudget.domain.bankpointage..")
            .or().resideInAPackage("..internal.notification.rules..")
            .should().dependOnClassesThat().areAssignableTo(PersistenceManager.class)
            .as("les moteurs de calcul et calculateurs purs ne doivent pas dépendre de PersistenceManager "
                    + "(doc/architecture/00-principes.md)");

    /**
     * Garde-fou d'architecture VT-400 : Les couches pures du domaine ne doivent pas
     * dépendre des DTO OpenAPI.
     */
    @ArchTest
    static final ArchRule PURE_DOMAIN_DOES_NOT_DEPEND_ON_OPENAPI_DTOS = noClasses()
            .that().resideInAPackage("..internal.calculation..")
            .or().resideInAPackage("..internal.model..")
            .or().resideInAPackage("com.moe.myfamilybudget.domain.budget..")
            .or().resideInAPackage("com.moe.myfamilybudget.domain.retirement..")
            .or().resideInAPackage("com.moe.myfamilybudget.domain.tax..")
            .or().resideInAPackage("com.moe.myfamilybudget.domain.wealth..")
            .or().resideInAPackage("com.moe.myfamilybudget.domain.bankpointage..")
            .or().resideInAPackage("..internal.notification.rules..")
            .should().dependOnClassesThat().resideInAPackage("com.moe.myfamilybudget.api..")
            .as("les couches pures du domaine (calculation, model, notification.rules) ne doivent pas dépendre "
                    + "des DTO/interfaces OpenAPI (com.moe.myfamilybudget.server.api..)");
}
