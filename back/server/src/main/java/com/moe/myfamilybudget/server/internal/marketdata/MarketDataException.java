package com.moe.myfamilybudget.server.internal.marketdata;

/**
 * Erreur d'accès ou de lecture d'une source de données de marché (réseau, HTTP, format inattendu).
 * Volontairement non vérifiée : le service d'agrégation la rattrape et conserve le dernier
 * instantané connu plutôt que de faire échouer l'application.
 */
public class MarketDataException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MarketDataException(String message) {
        super(message);
    }

    public MarketDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
