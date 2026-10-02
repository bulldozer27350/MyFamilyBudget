package com.moe.myfamilybudget.server.internal.port;

/**
 * Port du verrou de mutation du budget (VT-350b), responsabilite transverse sortie du domaine Fiscalite
 * par DB-061 : elle n'appartient a aucun domaine, seulement aux facades transactionnelles multi-domaines.
 */
public interface BudgetMutationLock {

    /**
     * A appeler en premier par une facade transactionnelle multi-domaines (parametres), avant toute ecriture,
     * pour que le verrou du budget soit toujours pris avant les verrous de lignes de la base (pas
     * d'interblocage entre deux facades). Sans transaction en cours, ne bloque rien.
     */
    void lockForCurrentTransaction();
}
