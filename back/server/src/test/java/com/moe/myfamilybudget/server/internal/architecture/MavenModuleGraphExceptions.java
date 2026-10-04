package com.moe.myfamilybudget.server.internal.architecture;

import com.moe.myfamilybudget.server.internal.architecture.MavenModuleGraphRules.Edge;
import java.util.List;

/**
 * Dérogations datées aux règles de graphe Maven (SILO-003, voir doc/architecture/21-plan-silotage.md).
 *
 * <p>Chaque entrée est une arête interdite par {@link MavenModuleGraphRules} qui existe encore et que le patch
 * indiqué supprime. La liste ne peut que décroître : {@link MavenModuleGraphTest} échoue si elle dépasse
 * {@link #FROZEN_SIZE}, si une entrée ne correspond plus à une arête interdite réelle (le patch qui retire
 * l'arête supprime la ligne et abaisse {@link #FROZEN_SIZE}) ou si le patch de suppression n'est pas nommé.
 */
final class MavenModuleGraphExceptions {

    /** Date de gel de la liste. */
    static final String FROZEN_ON = "2026-10-04";

    /** Taille maximale de la liste à la date de gel ; à abaisser à chaque suppression d'entrée. */
    static final int FROZEN_SIZE = 0;

    /** Dérogation : arête tolérée et patch qui la supprime. */
    record Tolerated(Edge edge, String removedBy) {}

    /** Liste vide depuis SILO-140 : plus aucune arête interdite tolérée entre modules. */
    static final List<Tolerated> ENTRIES = List.of();

    private MavenModuleGraphExceptions() {}
}
