package com.moe.myfamilybudget.server.internal.error;

import java.time.Instant;

/**
 * Format de réponse d'erreur JSON uniforme, renvoyé par {@link GlobalExceptionHandler} à la place
 * de la page d'erreur Spring par défaut (Whitelabel Error Page en HTML, ou corps vide selon les
 * cas). Permet au front-end de distinguer de façon fiable une erreur "attendue" (donnée invalide,
 * champ inconnu) d'une erreur inattendue, et d'afficher un message exploitable.
 *
 * @param status    code HTTP renvoyé (dupliqué ici pour que le corps soit auto-porteur, utile en
 *                  cas de journalisation ou de rejeu de la réponse côté client)
 * @param error     libellé court et stable de la catégorie d'erreur (ex. "BAD_REQUEST",
 *                  "INTERNAL_ERROR"), pensé pour être testé par le front-end sans dépendre du
 *                  message humain
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
