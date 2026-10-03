package com.moe.myfamilybudget.transition.port;

import com.moe.myfamilybudget.transition.model.BudgetDataModel;

/**
 * Port d'ecriture du snapshot global {@link BudgetDataModel} (CLEAN-020, MAVEN-103) : remplacement complet
 * (import JSON) et reinitialisation aux valeurs par defaut (reset).
 *
 * <p>Seul le composant de snapshot global de la couche applicative ({@code application.snapshot}) l'utilise ;
 * l'implementation est portee par {@code persistence}, qui delegue a {@code PersistenceManager}. Ces
 * operations ne s'appellent que dans une transaction deja ouverte et apres
 * {@link BudgetMutationLock#lockForCurrentTransaction()}.
 *
 * <p>Port de transition : il disparait avec {@code BudgetDataModel} (DB-1180).
 */
public interface GlobalBudgetSnapshotWriter {

    /** Remplace l'integralite du modele de donnees (import). */
    void setBudgetData(BudgetDataModel data);

    /** Reinitialise les donnees aux valeurs par defaut et renvoie le modele obtenu. */
    BudgetDataModel resetData();
}
