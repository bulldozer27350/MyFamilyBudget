package com.moe.myfamilybudget.server.internal.updatecheck;

import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Expose le dernier statut connu de disponibilité d'une mise à jour (voir
 * {@code UpdateCheckService}, {@code UpdateCheckScheduler}) et le déclenchement de la sauvegarde
 * de précaution avant mise à jour (voir {@code PreUpdateBackupService}). N'existe pas en profil
 * Docker (voir {@link Profile}) : l'appel renvoie alors une 404, traitée côté front comme "pas de
 * bandeau à afficher" (voir view/js/api.js#getUpdateStatus).
 */
@RestController
@RequestMapping("/update-check")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Profile("!docker")
class UpdateCheckController {

    private final UpdateCheckService updateCheckService;
    private final PreUpdateBackupService preUpdateBackupService;

    UpdateCheckController(UpdateCheckService updateCheckService, PreUpdateBackupService preUpdateBackupService) {
        this.updateCheckService = updateCheckService;
        this.preUpdateBackupService = preUpdateBackupService;
    }

    @GetMapping
    ResponseEntity<UpdateStatus> getStatus() {
        return ResponseEntity.ok(updateCheckService.getStatus());
    }

    /**
     * Déclenché par le bouton "Télécharger" du bandeau, avant l'ouverture du lien de
     * téléchargement : sauvegarde cohérente de la base en cours d'exécution, sans interrompre
     * l'application (voir {@code PreUpdateBackupService}). Un échec ne bloque jamais le
     * téléchargement côté front, il est seulement signalé à l'utilisateur.
     */
    @PostMapping("/backup")
    ResponseEntity<Map<String, Object>> backupBeforeUpdate() {
        Map<String, Object> body = new HashMap<>();
        preUpdateBackupService.backup().ifPresentOrElse(
                path -> {
                    body.put("success", true);
                    body.put("backupPath", path);
                },
                () -> body.put("success", false));
        return ResponseEntity.ok(body);
    }
}
