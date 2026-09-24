package com.moe.myfamilybudget.server.internal.enablebanking;

/**
 * Erreur d'accès ou de lecture Enable Banking (réseau, HTTP, certificat illisible, format
 * inattendu). Volontairement non vérifiée : le service de synchronisation la rattrape par compte,
 * pour qu'un compte en échec n'empêche pas les autres d'être synchronisés.
 */
public class EnableBankingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EnableBankingException(String message) {
        super(message);
    }

    public EnableBankingException(String message, Throwable cause) {
        super(message, cause);
    }
}
