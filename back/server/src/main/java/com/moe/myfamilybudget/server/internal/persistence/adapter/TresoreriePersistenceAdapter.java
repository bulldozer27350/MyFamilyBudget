package com.moe.myfamilybudget.server.internal.persistence.adapter;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.port.TresorerieWriter;

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
    public Map<String, Object> addTresorerieRow(String listKey, Map<String, Object> body) {
        return persistenceManager.addTresorerieRow(listKey, body);
    }

    @Override
    public void updateTresorerieRow(String listKey, String id, String field, Object value) {
        persistenceManager.updateTresorerieRow(listKey, id, field, value);
    }

    @Override
    public void removeTresorerieRow(String listKey, String id) {
        persistenceManager.removeTresorerieRow(listKey, id);
    }

    @Override
    public void applyTresorerieAjustement(String lineId, String kind, BigDecimal newMonthly) {
        persistenceManager.applyTresorerieAjustement(lineId, kind, newMonthly);
    }
}
