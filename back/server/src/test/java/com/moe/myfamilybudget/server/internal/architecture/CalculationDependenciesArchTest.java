package com.moe.myfamilybudget.server.internal.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.ChargeModel;
import com.moe.myfamilybudget.server.internal.model.IncomeModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.model.PointageCalculator;
import com.moe.myfamilybudget.server.internal.model.PointageModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;
import com.moe.myfamilybudget.server.internal.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.server.internal.model.TaxBracketModel;
import com.moe.myfamilybudget.server.internal.model.TaxCalculator;
import com.moe.myfamilybudget.server.internal.model.TaxChildModel;
import com.moe.myfamilybudget.server.internal.model.TaxRateOverrideModel;
import com.moe.myfamilybudget.server.internal.model.VariableIncomeModel;
import com.moe.myfamilybudget.server.internal.model.VariableOverrideModel;

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
 * {@link #POINTAGE_ENGINE_DOES_NOT_DEPEND_ON_BUDGET_MODELS}, non gelée.
 *
 * <p><b>Gel des violations existantes ({@link FreezingArchRule}).</b> À l'écriture de ce test,
 * {@code OverviewCalculationService} et {@code TresorerieCalculationService} dépendent encore
 * directement de {@code BudgetDataModel}. Plutôt que de casser le build immédiatement, la
 * règle est enveloppée dans une {@link FreezingArchRule} : les violations constatées au premier
 * lancement sont gelées dans le dossier {@code archunit_store} (voir {@code archunit.properties})
 * et n'échouent plus tant qu'elles ne s'aggravent pas ; toute <em>nouvelle</em> violation, elle,
 * fait échouer le test immédiatement.
 *
 * <p><b>Règle de vie du store, pour tout agent qui migre un domaine (RF-1xx à RF-9xx).</b> La
 * liste gelée ne doit évoluer que dans un sens : vers zéro. Concrètement :
 * <ul>
 *   <li>chaque patch de garde-fou de domaine (RF-103, RF-203, RF-302, RF-402, RF-502, RF-602,
 *       RF-703, RF-803, RF-902) qui débranche un moteur de {@code BudgetDataModel} doit relancer
 *       ce test avant de committer : {@code archunit.properties} autorise le store à se réduire
 *       automatiquement (violation qui disparaît) ;</li>
 *   <li>si une exécution fait apparaître une violation qui n'existait pas avant votre patch, ne
 *       la gelez pas silencieusement — c'est le garde-fou qui fonctionne, corrigez le code plutôt
 *       que d'élargir le périmètre gelé ;</li>
 *   <li>au patch RF-902 (dernier garde-fou de domaine listé), la liste gelée doit être vide ; à ce
 *       stade cette règle peut être durcie en {@code ArchRuleDefinition.noClasses()...} simple,
 *       sans {@code FreezingArchRule}.</li>
 * </ul>
 */
@AnalyzeClasses(packages = "com.moe.myfamilybudget", importOptions = ImportOption.DoNotIncludeTests.class)
class CalculationDependenciesArchTest {

    @ArchTest
    static final ArchRule CALCULATION_DOES_NOT_DEPEND_ON_BUDGET_DATA_MODEL = FreezingArchRule.freeze(
            noClasses()
                    .that().resideInAPackage("..internal.calculation..")
                    .should().dependOnClassesThat().areAssignableTo(BudgetDataModel.class)
                    .as("le package internal.calculation ne doit pas dépendre de BudgetDataModel "
                            + "(doc/architecture/00-principes.md) ; violations préexistantes gelées, "
                            + "voir la javadoc de cette classe et archunit.properties"));

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
}
