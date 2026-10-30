package com.moe.myfamilybudget.web.error;

import java.time.Instant;

/**
 * Format de réponse d'erreur JSON uniforme, renvoyé par {@link GlobalExceptionHandler} à la place
 * de la page d'erreur Spring par défaut.
 *
 * @param status    code HTTP renvoyé
 * @param error     libellé court et stable de la catégorie d'erreur (ex. "BAD_REQUEST", "INTERNAL_ERROR")
 * @param message   message lisible, destiné à l'affichage ou au diagnostic
 * @param path      chemin de la requête ayant provoqué l'erreur
 * @param timestamp instant de la réponse
 */
public record ApiErrorResponse(
        int status,
        String error,
        String message,
        String path,
        Instant timestamp
) {

    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return new ApiErrorResponse(status, error, message, path, Instant.now());
    }
}
