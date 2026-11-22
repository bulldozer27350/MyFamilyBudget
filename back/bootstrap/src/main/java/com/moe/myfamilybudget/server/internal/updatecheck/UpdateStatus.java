package com.moe.myfamilybudget.server.internal.updatecheck;

import java.time.Instant;

/**
 * Instantané exposé par {@code UpdateCheckController} : résultat du dernier contrôle de version
 * effectué par {@code UpdateCheckService}. Jamais {@code null} une fois le bean construit (voir
 * son constructeur) : au tout premier démarrage, avant le premier passage du planificateur,
 * {@code updateAvailable} vaut simplement {@code false}.
 *
 * @param installDir chemin absolu du dossier contenant le jar en cours d'exécution (voir
 *     {@code java.base}'s {@code ApplicationHome}), pour guider précisément l'utilisateur sur où
 *     dézipper la mise à jour.
 */
public record UpdateStatus(
        boolean updateAvailable,
        String currentVersion,
        String latestVersion,
        String releaseUrl,
        String downloadUrl,
        String releaseNotes,
        String publishedAt,
        String installDir,
        Instant checkedAt) {
}
