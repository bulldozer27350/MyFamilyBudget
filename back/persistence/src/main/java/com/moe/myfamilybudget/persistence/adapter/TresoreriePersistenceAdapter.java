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

/**
 * Adaptateur de persistance pour {@link TresorerieWriter} (DB-031). Les lectures du domaine passent
 * deja par les adaptateurs Budget, Patrimoine et Banque : cet adaptateur ne porte que l'ecriture.
 */
@Component
public class TresoreriePersistenceAdapter implements TresorerieWriter {

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
}
