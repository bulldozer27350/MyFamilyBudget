package com.moe.myfamilybudget.server.internal.command;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.domain.treasury.port.TresorerieAdjustmentKind;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieLineField;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieList;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSettingField;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieWriter;

/**
 * Service de commande du domaine Tresorerie (RF-A00, DB-031).
 * Unique point d'ecriture des lignes de tresorerie (revenus, charges, depenses ponctuelles, revenus
 * variables et ajustements) : valide la commande puis delegue au port {@link TresorerieWriter}. N'a plus
 * de dependance directe vers {@code PersistenceManager}.
 *
 * <p>DB-050 : la liste et la nature d'ajustement sont des enums ({@link TresorerieList},
 * {@link TresorerieAdjustmentKind}) interpretes a la frontiere REST ; ce service ne manipule plus de
 * {@code listKey} en chaine.
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

    public Map<String, Object> addTresorerieRow(TresorerieList list, Map<String, Object> body) {
        require(list, "La liste de tresorerie");
        return tresorerieWriter.addTresorerieRow(list, body);
    }

    public void updateTresorerieRow(TresorerieList list, String id, TresorerieLineField field, Object value) {
        require(list, "La liste de tresorerie");
        require(id, "L'identifiant de la ligne");
        require(field, "Le champ de la ligne");
        tresorerieWriter.updateTresorerieRow(list, id, field, value);
    }

    public void removeTresorerieRow(TresorerieList list, String id) {
        require(list, "La liste de tresorerie");
        require(id, "L'identifiant de la ligne");
        tresorerieWriter.removeTresorerieRow(list, id);
    }

    public void applyTresorerieAjustement(String lineId, TresorerieAdjustmentKind kind, BigDecimal newMonthly) {
        require(lineId, "L'identifiant de la ligne a ajuster");
        require(kind, "Le type de ligne a ajuster");
        require(newMonthly, "Le nouveau montant mensuel");
        tresorerieWriter.applyTresorerieAjustement(lineId, kind, newMonthly);
    }

    /**
     * SET-020 : owner des paramètres de la famille Trésorerie exposés par {@code PATCH /settings} (pivot,
     * solde de départ, sweep, plafonds de cash). Une valeur {@code null} reste acceptée (contrat historique).
     */
    public void updateTresorerieSetting(TresorerieSettingField field, Object value) {
        require(field, "Le parametre de tresorerie");
        tresorerieWriter.updateTresorerieSetting(field, value);
    }

    private static void require(Object value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " est obligatoire");
        }
    }
}
