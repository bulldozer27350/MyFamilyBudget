package com.moe.myfamilybudget.domain.treasury.port;

import java.util.List;

import com.moe.myfamilybudget.domain.treasury.model.ChargeModel;
import com.moe.myfamilybudget.domain.treasury.model.IncomeModel;
import com.moe.myfamilybudget.domain.treasury.model.OneOffExpenseModel;
import com.moe.myfamilybudget.domain.treasury.model.VariableIncomeModel;
import com.moe.myfamilybudget.domain.treasury.model.VariableOverrideModel;
import com.moe.myfamilybudget.domain.treasury.model.TresorerieSettingsModel;

/**
 * Port d'import et de réinitialisation du silo Trésorerie (SILO-119, lot B1). L'export passe par
 * {@link TresorerieSettingsReader} (paramètres) et par le port de lecture des lignes de trésorerie.
 *
 * <p>Ces opérations ne s'appellent que dans une transaction déjà ouverte et après la prise du verrou de
 * mutation. Elles ne modifient que les données du silo Trésorerie.
 */
public interface TresorerieSnapshotWriter {

    /** Remplace les paramètres et les lignes de trésorerie ; une liste {@code null} est lue comme vide. */
    void replace(TresorerieSettingsModel settings, List<IncomeModel> incomes, List<ChargeModel> charges,
                 List<OneOffExpenseModel> oneoffExpenses, List<VariableIncomeModel> variableIncomes,
                 List<VariableOverrideModel> variableOverrides);

    /** Remet le silo Trésorerie à ses valeurs par défaut (aucune ligne). */
    void reset();
}
