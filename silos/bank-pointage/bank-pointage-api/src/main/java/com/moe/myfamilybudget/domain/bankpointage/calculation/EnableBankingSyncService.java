package com.moe.myfamilybudget.domain.bankpointage.calculation;

import com.moe.myfamilybudget.domain.bankpointage.model.EnableBankingException;
import com.moe.myfamilybudget.domain.bankpointage.model.EnableBankingSyncResult;

/**
 * Synchronisation des transactions bancaires via Enable Banking (DSP2), rattachée au silo Banque (D7).
 * L'implémentation vit dans {@code bank-pointage-core} ({@code DefaultEnableBankingSyncService}) ; le
 * déclenchement (bouton du front, planificateur) reste côté web et composition root.
 */
public interface EnableBankingSyncService {

    /** Vrai si le certificat, l'identifiant d'application et les comptes sont fournis et lisibles. */
    boolean isConfigured();

    /** Message explicatif à afficher ou journaliser quand {@link #isConfigured()} est faux. */
    String unavailableReason();

    /** @throws EnableBankingException si la synchronisation n'est pas configurée */
    EnableBankingSyncResult sync();
}
