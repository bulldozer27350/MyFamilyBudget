package com.moe.myfamilybudget.server.internal.updatecheck;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Contrôle de mise à jour de la distribution portable (voir {@code UpdateCheckScheduler} pour la
 * planification, {@code UpdateCheckController} pour l'exposition HTTP). N'existe pas en profil
 * Docker (voir {@link Profile}) : le conteneur reste à jour par construction de l'infra qui
 * l'héberge, ses utilisateurs n'ont aucune mise à jour manuelle à faire.
 *
 * <p>Ne fait jamais échouer le démarrage ni l'usage normal de l'application : toute erreur réseau
 * laisse simplement le dernier statut connu (ou l'état initial "pas de mise à jour") inchangé
 * (voir {@code GithubReleaseClient}).
 */
@Component
@Profile("!docker")
class UpdateCheckService {

    private static final Logger log = LoggerFactory.getLogger(UpdateCheckService.class);

    private final GithubReleaseClient githubReleaseClient;
    private final Optional<BuildProperties> buildProperties;
    private final String githubApiBaseUrl;
    private final String githubOwner;
    private final String githubRepo;
    private final int timeoutSeconds;
    private final String installDir;
    private final AtomicReference<UpdateStatus> status;

    UpdateCheckService(
            GithubReleaseClient githubReleaseClient,
            Optional<BuildProperties> buildProperties,
            @Value("${myfamilybudget.update-check.github.api-base-url:https://api.github.com}") String githubApiBaseUrl,
            @Value("${myfamilybudget.update-check.github.owner:bulldozer27350}") String githubOwner,
            @Value("${myfamilybudget.update-check.github.repo:MyFamilyBudget}") String githubRepo,
            @Value("${myfamilybudget.update-check.timeout-seconds:5}") int timeoutSeconds) {
        this.githubReleaseClient = githubReleaseClient;
        this.buildProperties = buildProperties;
        this.githubApiBaseUrl = githubApiBaseUrl;
        this.githubOwner = githubOwner;
        this.githubRepo = githubRepo;
        this.timeoutSeconds = timeoutSeconds;
        this.installDir = new ApplicationHome(UpdateCheckService.class).getDir().getAbsolutePath();
        this.status = new AtomicReference<>(new UpdateStatus(
                false, currentVersion(), null, null, null, null, null, installDir, Instant.now()));
    }

    UpdateStatus getStatus() {
        return status.get();
    }

    /** Appelé périodiquement par {@code UpdateCheckScheduler}. */
    void refresh() {
        githubReleaseClient.fetchLatestRelease(githubApiBaseUrl, githubOwner, githubRepo, timeoutSeconds)
                .ifPresent(release -> {
                    String current = currentVersion();
                    String latest = stripLeadingV(release.tagName());
                    boolean available = latest != null && isNewer(latest, current);
                    status.set(new UpdateStatus(
                            available,
                            current,
                            latest,
                            release.htmlUrl(),
                            release.downloadUrl(),
                            release.releaseNotes(),
                            release.publishedAt(),
                            installDir,
                            Instant.now()));
                    if (available) {
                        log.info("Nouvelle version disponible : {} (version actuelle : {})", latest, current);
                    }
                });
    }

    private String currentVersion() {
        // BuildProperties n'est présent que si le jar a été construit avec le goal Maven
        // "build-info" (voir back/bootstrap/pom.xml) : absent en lancement depuis l'IDE ou les
        // tests, auquel cas aucune mise à jour ne peut être détectée comme disponible.
        return buildProperties.map(BuildProperties::getVersion).orElse("0.0.0");
    }

    private static String stripLeadingV(String tagName) {
        if (tagName == null || tagName.isBlank()) return null;
        char first = tagName.charAt(0);
        return (first == 'v' || first == 'V') ? tagName.substring(1) : tagName;
    }

    /** Comparaison SemVer majeur.mineur.correctif ; tout suffixe (-SNAPSHOT, +build…) est ignoré. */
    private static boolean isNewer(String latest, String current) {
        int[] l = toParts(latest);
        int[] c = toParts(current);
        for (int i = 0; i < 3; i++) {
            if (l[i] != c[i]) {
                return l[i] > c[i];
            }
        }
        return false;
    }

    private static int[] toParts(String version) {
        String numeric = version.split("[-+]", 2)[0];
        String[] tokens = numeric.split("\\.");
        int[] parts = new int[3];
        for (int i = 0; i < 3 && i < tokens.length; i++) {
            try {
                parts[i] = Integer.parseInt(tokens[i].trim());
            } catch (NumberFormatException e) {
                parts[i] = 0;
            }
        }
        return parts;
    }
}
