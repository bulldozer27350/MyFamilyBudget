package com.moe.myfamilybudget.server.internal.updater;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.ChargeModel;
import com.moe.myfamilybudget.server.internal.model.IncomeModel;
import com.moe.myfamilybudget.server.internal.model.OneOffExpenseModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.model.VariableIncomeModel;
import com.moe.myfamilybudget.server.internal.model.VariableOverrideModel;

/**
 * Remplace l'unique gros bloc if/else de PersistenceManager.updateTresorerieRow.
 *
 * Ce qui change par rapport à l'ancien code :
 *  - le "quel champ, quelle fonction de conversion" est désormais porté par les classes
 *    {@code *FieldUpdaters} (une table, pas une chaîne de ternaires) ;
 *  - un {@code listKey} ou un {@code field} inconnu lève {@link UnknownTresorerieFieldException}
 *    au lieu de renvoyer silencieusement l'état inchangé.
 *
 * Ce qui NE change PAS : le comportement pour tout champ/listKey déjà valide reste identique
 * (mêmes valeurs par défaut, mêmes conversions) — sauf pour "placements", où un bug est corrigé
 * au passage : l'ancien code effaçait l'historique de valorisation du placement à chaque édition
 * de champ (voir le commentaire dans PlacementFieldUpdaters).
 */
public final class TresorerieFieldUpdateDispatcher {

    private TresorerieFieldUpdateDispatcher() {
    }

    public static BudgetDataModel update(BudgetDataModel base, String listKey, String id, String field, Object value) {
        if ("incomes".equalsIgnoreCase(listKey)) {
            List<IncomeModel> list = updateInList(base.getEffectiveIncomes(), id, field, value,
                    IncomeFieldUpdaters.instance(), IncomeModel::id, listKey);
            return base.withIncomes(list);
        }
        if ("charges".equalsIgnoreCase(listKey)) {
            List<ChargeModel> list = updateInList(base.getEffectiveCharges(), id, field, value,
                    ChargeFieldUpdaters.instance(), ChargeModel::id, listKey);
            return base.withCharges(list);
        }
        if ("oneoff".equalsIgnoreCase(listKey)) {
            List<OneOffExpenseModel> list = updateInList(base.getEffectiveOneoff(), id, field, value,
                    OneOffFieldUpdaters.instance(), OneOffExpenseModel::id, listKey);
            return base.withOneoff(list);
        }
        if ("variableIncomes".equalsIgnoreCase(listKey)) {
            List<VariableIncomeModel> list = updateInList(base.getEffectiveVariableIncomes(), id, field, value,
                    VariableIncomeFieldUpdaters.instance(), VariableIncomeModel::id, listKey);
            return base.withVariableIncomes(list);
        }
        if ("variableOverrides".equalsIgnoreCase(listKey)) {
            List<VariableOverrideModel> list = updateInList(base.getEffectiveVariableOverrides(), id, field, value,
                    VariableOverrideFieldUpdaters.instance(), VariableOverrideModel::id, listKey);
            return base.withVariableOverrides(list);
        }
        if ("placements".equalsIgnoreCase(listKey)) {
            List<PlacementModel> list = updateInList(base.getEffectivePlacements(), id, field, value,
                    PlacementFieldUpdaters.instance(), PlacementModel::id, listKey);
            return base.withPlacements(list);
        }

        throw new UnknownTresorerieFieldException(listKey, null);
    }

    private static <T> List<T> updateInList(List<T> source, String id, String field, Object value,
                                             RecordFieldUpdater<T> updater, Function<T, String> idExtractor,
                                             String listKey) {
        List<T> result = new ArrayList<>(source.size());
        for (T row : source) {
            if (Objects.equals(idExtractor.apply(row), id)) {
                T updatedRow = updater.update(row, field, value)
                        .orElseThrow(() -> new UnknownTresorerieFieldException(listKey, field));
                result.add(updatedRow);
            } else {
                result.add(row);
            }
        }
        return result;
    }
}
