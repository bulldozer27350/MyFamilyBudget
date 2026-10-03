package com.moe.myfamilybudget.domain.credit.port;

import java.util.Map;

/**
 * Port d'ecriture pour le domaine Credit (DB-041). Seul le command service du domaine
 * ({@code LoanCommandService}) l'utilise : il isole le domaine du {@code PersistenceManager} et du
 * command Patrimoine, et prepare la bascule JPA (DB-1041) sans changer l'appelant.
 *
 * <p>Le corps generique est conserve tel quel : son retrait releve de DB-050.
 */
public interface LoanWriter {

    /** Cree ou remplace un pret ; renvoie la ligne enregistree (dont son {@code id}). */
    Map<String, Object> saveLoanRow(Map<String, Object> body);

    void deleteLoanRow(String id);
}
