package com.moe.myfamilybudget.server.internal.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Garde-fou SILO-118 (voir doc/architecture/21-plan-silotage.md) : les silos Objectifs et Notifications
 * construisent leurs entrées à partir de fragments lus via les ports de lecture ({@code GoalReader},
 * {@code BankReader}, {@code PatrimoineReader}, {@code TresorerieSettingsReader}) et de leurs propres
 * paramètres ({@code ObjectifsSettingsService}, {@code NotificationSettingsService}).
 *
 * <p>Aucune de leurs classes ne doit revenir aux types globaux de transition : {@code BudgetDataModel},
 * {@code SettingsModel}, {@code SettingsReader} ou son assembleur {@code SettingsModelAssembler}. Règle
 * stricte, sans gel : le silo ne figure pas dans la liste de {@link BudgetDataModelAllowList} et ne doit
 * jamais y entrer.
 */
@AnalyzeClasses(packages = "com.moe.myfamilybudget", importOptions = ImportOption.DoNotIncludeTests.class)
class GoalsNotificationsFragmentInputsArchTest {

    private static final String GOALS_AND_NOTIFICATIONS_CLASSES =
            "com\\.moe\\.myfamilybudget\\.("
                    + "domain\\.goals\\..*"
                    + "|domain\\.notifications\\..*"
                    + "|server\\.internal\\.notification\\..*"
                    + "|server\\.internal\\.impl\\.NotificationsServiceImpl"
                    + "|server\\.internal\\.mapper\\.NotificationsMapper"
                    + "|server\\.internal\\.calculation\\.JpaObjectifsSettingsStore"
                    + "|application\\.factory\\.NotificationInputFactory"
                    + "|application\\.command\\.GoalCommandService"
                    + "|application\\.settings\\.ObjectifsSettings(Service|Store)"
                    + ")(\\$.*)?";

    private static final String GLOBAL_TRANSITION_TYPES =
            "com\\.moe\\.myfamilybudget\\.("
                    + "transition\\.model\\.(BudgetDataModel|SettingsModel)"
                    + "|transition\\.port\\.SettingsReader"
                    + "|application\\.settings\\.SettingsModelAssembler"
                    + ")(\\$.*)?";

    @ArchTest
    static final ArchRule GOALS_AND_NOTIFICATIONS_DO_NOT_USE_THE_GLOBAL_MODELS = noClasses()
            .that().haveNameMatching(GOALS_AND_NOTIFICATIONS_CLASSES)
            .should().dependOnClassesThat().haveNameMatching(GLOBAL_TRANSITION_TYPES)
            .as("les silos Objectifs et Notifications lisent des fragments par ports, jamais BudgetDataModel, "
                    + "SettingsModel ni SettingsReader (SILO-118)");
}
