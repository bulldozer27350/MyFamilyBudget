package com.moe.myfamilybudget.server.internal.command;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.server.internal.port.TresorerieWriter;

/**
 * Service de commande du domaine Tresorerie (RF-A00, DB-031).
 * Unique point d'ecriture des lignes de tresorerie (revenus, charges, depenses ponctuelles, revenus
 * variables et ajustements) : valide la commande puis delegue au port {@link TresorerieWriter}. N'a plus
 * de dependance directe vers {@code PersistenceManager}.
 *
 * <p>Les identifiants issus de l'URL ne sont jamais {@code null} cote REST : un {@code null} est donc une
 * erreur de programmation, refusee avant toute ecriture ({@link IllegalArgumentException}, traduite en
 * 400 par le gestionnaire d'erreurs). Un corps {@code null} reste accepte pour
 * {@link #addTresorerieRow} (creation d'une ligne par defaut) et une valeur {@code null} pour
 * {@link #updateTresorerieRow} (effacement du champ), conformement au contrat historique de l'API.
 */
@Service
public class TresorerieCommandService {

    private final TresorerieWriter tresorerieWriter;

    public TresorerieCommandService(TresorerieWriter tresorerieWriter) {
        this.tresorerieWriter = tresorerieWriter;
    }

    public Map<String, Object> addTresorerieRow(String listKey, Map<String, Object> body) {
        require(listKey, "La liste de tresorerie (listKey)");
        return tresorerieWriter.addTresorerieRow(listKey, body);
    }

    public void updateTresorerieRow(String listKey, String id, String field, Object value) {
        require(listKey, "La liste de tresorerie (listKey)");
        require(id, "L'identifiant de la ligne");
        require(field, "Le champ de la ligne");
        tresorerieWriter.updateTresorerieRow(listKey, id, field, value);
    }

    public void removeTresorerieRow(String listKey, String id) {
        require(listKey, "La liste de tresorerie (listKey)");
        require(id, "L'identifiant de la ligne");
        tresorerieWriter.removeTresorerieRow(listKey, id);
    }

    public void applyTresorerieAjustement(String lineId, String kind, BigDecimal newMonthly) {
        require(lineId, "L'identifiant de la ligne a ajuster");
        require(kind, "Le type de ligne a ajuster");
        require(newMonthly, "Le nouveau montant mensuel");
        tresorerieWriter.applyTresorerieAjustement(lineId, kind, newMonthly);
    }

    private static void require(Object value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " est obligatoire");
        }
    }
}
