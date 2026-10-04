package com.moe.myfamilybudget.server.internal.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.moe.myfamilybudget.transition.model.BudgetDataModel;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Garde-fou CLEAN-010 puis SILO-002 (voir doc/architecture/19-inventaire-budget-data-model.md et
 * doc/architecture/21-plan-silotage.md) : {@link BudgetDataModel} est un snapshot global destiné à
 * disparaître (SILO-230).
 *
 * <p>Seules les classes de {@link BudgetDataModelAllowList} peuvent en dépendre. La liste est
 * fermée (aucune règle par package), datée, et chaque entrée nomme le patch SILO-xxx qui la
 * supprime. Elle ne peut que décroître : toute nouvelle dépendance, toute entrée ajoutée, toute
 * entrée sans patch de suppression ou dont la classe a disparu fait échouer le build.
 */
@AnalyzeClasses(packages = "com.moe.myfamilybudget", importOptions = ImportOption.DoNotIncludeTests.class)
class BudgetDataModelUsageArchTest {

    private static final Pattern REMOVAL_PATCH = Pattern.compile("SILO-\\d{3}");

    private static final String MODEL_NAME = BudgetDataModel.class.getName();

    private static String topLevelName(JavaClass javaClass) {
        String name = javaClass.getName();
        int nested = name.indexOf('$');
        return nested < 0 ? name : name.substring(0, nested);
    }

    private static Set<String> allowedNames() {
        Set<String> names = new HashSet<>();
        for (BudgetDataModelAllowList.Entry entry : BudgetDataModelAllowList.ENTRIES) {
            names.add(entry.className());
        }
        return names;
    }

    private static final DescribedPredicate<JavaClass> NOT_ALLOWED_TO_USE_THE_MODEL =
            new DescribedPredicate<>("ne figurent pas dans la liste fermée BudgetDataModelAllowList") {
                private final Set<String> allowed = allowedNames();

                @Override
                public boolean test(JavaClass javaClass) {
                    String name = topLevelName(javaClass);
                    return !MODEL_NAME.equals(name) && !allowed.contains(name);
                }
            };

    @ArchTest
    static final ArchRule BUDGET_DATA_MODEL_IS_ONLY_USED_BY_THE_CLOSED_LIST = noClasses()
            .that(NOT_ALLOWED_TO_USE_THE_MODEL)
            .should().dependOnClassesThat().areAssignableTo(BudgetDataModel.class)
            .as("BudgetDataModel n'est consommé que par les classes de la liste fermée SILO-002 (BudgetDataModelAllowList)");

    @ArchTest
    static void every_allowed_class_still_exists(JavaClasses classes) {
        for (BudgetDataModelAllowList.Entry entry : BudgetDataModelAllowList.ENTRIES) {
            if (!classes.contain(entry.className())) {
                throw new AssertionError("Entrée périmée dans BudgetDataModelAllowList (classe introuvable) : "
                        + entry.className() + " ; supprimer la ligne et abaisser FROZEN_SIZE");
            }
        }
    }

    @ArchTest
    static void allow_list_only_shrinks_and_names_its_removal_patch(JavaClasses classes) {
        int size = BudgetDataModelAllowList.ENTRIES.size();
        if (size > BudgetDataModelAllowList.FROZEN_SIZE) {
            throw new AssertionError("BudgetDataModelAllowList ne peut que décroître : " + size
                    + " entrées pour un maximum de " + BudgetDataModelAllowList.FROZEN_SIZE
                    + " (gel du " + BudgetDataModelAllowList.FROZEN_ON + ")");
        }
        Set<String> seen = new HashSet<>();
        for (BudgetDataModelAllowList.Entry entry : BudgetDataModelAllowList.ENTRIES) {
            if (!seen.add(entry.className())) {
                throw new AssertionError("Entrée en double dans BudgetDataModelAllowList : " + entry.className());
            }
            if (entry.removedBy() == null || !REMOVAL_PATCH.matcher(entry.removedBy()).matches()) {
                throw new AssertionError("Entrée sans patch de suppression SILO-xxx : " + entry.className());
            }
        }
    }
}
