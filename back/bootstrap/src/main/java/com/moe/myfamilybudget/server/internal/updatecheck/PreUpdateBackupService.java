package com.moe.myfamilybudget.server.internal.updatecheck;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Sauvegarde de précaution de la base H2, déclenchée par le clic sur "Télécharger" du bandeau de
 * mise à jour (voir {@code UpdateCheckController}), avant l'ouverture du lien de téléchargement.
 *
 * <p>Utilise la commande native H2 {@code BACKUP TO} : un instantané cohérent de la base est
 * produit sans arrêter ni verrouiller l'application (H2 gère lui-même la cohérence), contrairement
 * à une simple copie du fichier {@code .mv.db} qui pourrait capturer un état intermédiaire si
 * l'application écrit au même moment.
 *
 * <p>Un seul fichier, toujours écrasé : chaque clic ne conserve que la dernière sauvegarde
 * "avant mise à jour", pas un historique. N'existe qu'en profil non-Docker (voir
 * {@code UpdateCheckService}) : la base Postgres du profil Docker n'est pas concernée par ce
 * mécanisme, propre à la distribution portable H2.
 */
@Component
@Profile("!docker")
class PreUpdateBackupService {

    private static final Logger log = LoggerFactory.getLogger(PreUpdateBackupService.class);
    private static final String BACKUP_FILE_NAME = "myfamilybudget-pre-update-backup.zip";

    private final DataSource dataSource;

    PreUpdateBackupService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * @return le chemin absolu du fichier de sauvegarde si réussie, vide sinon (erreur disque, ou
     *     base non H2 — ne doit normalement jamais arriver hors profil Docker). N'interrompt
     *     jamais l'appelant par exception : un échec de sauvegarde ne doit pas bloquer le
     *     téléchargement de la mise à jour côté front.
     */
    Optional<String> backup() {
        try {
            Path installDir = new ApplicationHome(getClass()).getDir().toPath();
            Path backupDir = installDir.resolve("data").resolve("backups");
            Files.createDirectories(backupDir);
            Path backupFile = backupDir.resolve(BACKUP_FILE_NAME);
            Files.deleteIfExists(backupFile);

            // Chemin en slash, y compris sous Windows : évite toute ambiguïté dans le littéral SQL.
            String sqlPath = backupFile.toAbsolutePath().toString().replace('\\', '/').replace("'", "''");
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.execute("BACKUP TO '" + sqlPath + "'");
            }
            log.info("Sauvegarde pré-mise à jour créée : {}", backupFile);
            return Optional.of(backupFile.toAbsolutePath().toString());
        } catch (SQLException | IOException | RuntimeException e) {
            log.warn("Échec de la sauvegarde pré-mise à jour ({})", e.getMessage());
            return Optional.empty();
        }
    }
}
