package com.moe.myfamilybudget.transition.port;

/**
 * Port d'écriture de la notion Hypothèses économiques (SET-020). Seul
 * {@code EconomicAssumptionsCommandService} l'utilise. Le stockage physique reste partagé avec les
 * autres paramètres ; sa séparation relève des patchs {@code DB-xxx}.
 */
public interface EconomicAssumptionsWriter {

    /** Met à jour {@code inflationRate} ; une valeur illisible retombe sur la valeur par défaut historique. */
    void updateInflationRate(Object value);
}
