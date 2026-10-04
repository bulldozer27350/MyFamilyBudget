package com.moe.myfamilybudget.persistence.adapter;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieAdjustmentKind;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieLineField;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieList;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSettingField;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieWriter;
import java.util.List;
import com.moe.myfamilybudget.domain.treasury.model.ChargeModel;
import com.moe.myfamilybudget.domain.treasury.model.IncomeModel;
import com.moe.myfamilybudget.domain.treasury.model.OneOffExpenseModel;
import com.moe.myfamilybudget.domain.treasury.model.VariableIncomeModel;
import com.moe.myfamilybudget.domain.treasury.model.VariableOverrideModel;
import com.moe.myfamilybudget.domain.treasury.model.TresorerieSettingsModel;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSnapshotWriter;

/**
 * Adaptateur de persistance pour {@link TresorerieWriter} (DB-031). Les lectures du domaine passent
 * deja par les adaptateurs Budget, Patrimoine et Banque : cet adaptateur ne porte que l'ecriture.
 */
@Component
public class TresoreriePersistenceAdapter implements TresorerieWriter, TresorerieSnapshotWriter {

    private final PersistenceManager persistenceManager;

    public TresoreriePersistenceAdapter(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    public Map<String, Object> addTresorerieRow(TresorerieList list, Map<String, Object> body) {
        return persistenceManager.writeAndGet(m -> m.addTresorerieRow(list.key(), body));
    }

    @Override
    public void updateTresorerieRow(TresorerieList list, String id, TresorerieLineField field, Object value) {
        persistenceManager.write(m -> m.updateTresorerieRow(list.key(), id, field.key(), value));
    }

    @Override
    public void removeTresorerieRow(TresorerieList list, String id) {
        persistenceManager.write(m -> m.removeTresorerieRow(list.key(), id));
    }

    @Override
    public void applyTresorerieAjustement(String lineId, TresorerieAdjustmentKind kind, BigDecimal newMonthly) {
        persistenceManager.write(m -> m.applyTresorerieAjustement(lineId, kind.kind(), newMonthly));
    }

    /**
     * SET-020 / SET-030 : le stockage physique des paramètres reste partagé ({@code SettingsEntity}) ; la
     * mutation de transition est propre à la famille Trésorerie. Sa séparation relève des patchs DB-xxx.
     */
    @Override
    public void updateTresorerieSetting(TresorerieSettingField field, Object value) {
        persistenceManager.write(m -> m.updateTresorerieSetting(field, value));
    }

    /** SILO-119 (lot B1) : import du silo Trésorerie (paramètres et lignes de trésorerie). */
    @Override
    public void replace(TresorerieSettingsModel settings, List<IncomeModel> incomes, List<ChargeModel> charges,
                        List<OneOffExpenseModel> oneoffExpenses, List<VariableIncomeModel> variableIncomes,
                        List<VariableOverrideModel> variableOverrides) {
        persistenceManager.write(m -> m.replaceTresorerieSnapshot(settings, incomes, charges, oneoffExpenses,
                variableIncomes, variableOverrides));
    }

    /** SILO-119 (lot B1) : remise à zéro du silo Trésorerie. */
    @Override
    public void reset() {
        persistenceManager.write(m -> m.resetTresorerieSnapshot());
    }
}
