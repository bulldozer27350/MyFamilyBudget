package com.moe.myfamilybudget.server.internal.error;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.moe.myfamilybudget.server.internal.updater.UnknownTresorerieFieldException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Point d'entrée unique de gestion des erreurs pour tous les {@code @RestController} de
 * l'application (audit-mitigation-plan.md, point 2, étape 1).
 *
 * Avant ce handler, une exception non rattrapée localement remontait jusqu'à Spring, qui renvoyait
 * soit la Whitelabel Error Page HTML par défaut, soit un corps JSON générique et peu exploitable
 * ({@code {"timestamp":...,"status":500,"error":"Internal Server Error","path":"..."}} sans
 * message ni journalisation applicative). Ce handler :
 *  - renvoie systématiquement un corps {@link ApiErrorResponse} homogène, quelle que soit
 *    l'exception ;
 *  - journalise chaque erreur côté serveur (au niveau WARN pour les erreurs "attendues" imputables
 *    à la requête, ERROR avec la stack trace complète pour le reste), ce qui était totalement
 *    absent auparavant pour la plupart des {@code catch (Exception e)} du code ;
 *  - distingue les erreurs de requête (400) des erreurs serveur inattendues (500), alors que de
 *    nombreux cas remontaient auparavant en 500 générique ou étaient masqués en amont par un
 *    {@code return null;} silencieux (voir {@link DataParsingException}).
 *
 * Les gestionnaires les plus spécifiques sont déclarés avant le gestionnaire générique
 * {@link Exception} : Spring sélectionne le {@code @ExceptionHandler} le plus précis pour le type
 * effectif de l'exception levée, l'ordre de déclaration dans la classe n'a pas d'incidence.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Champ ou type de ligne de trésorerie inconnu (voir point 3 de l'audit / dispatcher de mise
     * à jour). Erreur de requête client sans ambiguïté : 400 Bad Request.
     */
    @ExceptionHandler(UnknownTresorerieFieldException.class)
    public ResponseEntity<ApiErrorResponse> handleUnknownField(UnknownTresorerieFieldException e,
                                                                HttpServletRequest request) {
        LOG.warn("Requête de mise à jour de trésorerie invalide sur {} : {}", request.getRequestURI(), e.getMessage());
        return badRequest(e.getMessage(), request);
    }

    /**
     * Donnée reçue trop mal formée pour être exploitée de façon fiable dans un calcul financier
     * (voir {@link DataParsingException}). Erreur de requête client : 400 Bad Request.
     */
    @ExceptionHandler(DataParsingException.class)
    public ResponseEntity<ApiErrorResponse> handleDataParsing(DataParsingException e,
                                                               HttpServletRequest request) {
        LOG.warn("Donnée invalide reçue sur {} : {}", request.getRequestURI(), e.getMessage(), e);
        return badRequest(e.getMessage(), request);
    }

    /**
     * Argument invalide levé explicitement par le code métier (ex. paramètre de requête absent ou
     * incohérent). Erreur de requête client : 400 Bad Request.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException e,
                                                                   HttpServletRequest request) {
        LOG.warn("Argument invalide sur {} : {}", request.getRequestURI(), e.getMessage());
        return badRequest(e.getMessage(), request);
    }

    /**
     * Échec de validation Bean Validation (annotations {@code @Valid} sur les DTO générés par
     * OpenAPI). Regroupe les messages de chaque champ en erreur dans un seul message lisible.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException e,
                                                              HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + " : " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        LOG.warn("Validation échouée sur {} : {}", request.getRequestURI(), message);
        return badRequest(message.isBlank() ? "Requête invalide." : message, request);
    }

    /**
     * Filet de sécurité générique : toute exception non gérée plus spécifiquement finit ici plutôt
     * que dans la page d'erreur par défaut de Spring. Toujours journalisée en ERROR avec la stack
     * trace complète, car par définition ce cas n'était pas anticipé.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        LOG.error("Erreur inattendue sur {} {}", request.getMethod(), request.getRequestURI(), e);
        ApiErrorResponse body = ApiErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_ERROR",
                "Une erreur inattendue est survenue. Consultez les journaux serveur pour le détail.",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private ResponseEntity<ApiErrorResponse> badRequest(String message, HttpServletRequest request) {
        ApiErrorResponse body = ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "BAD_REQUEST",
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
