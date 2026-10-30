package com.moe.myfamilybudget.web.error;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.moe.myfamilybudget.application.error.DataParsingException;
import com.moe.myfamilybudget.domain.treasury.port.UnknownTresorerieFieldException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Point d'entrée unique de gestion des erreurs pour tous les {@code @RestController} de
 * l'application (audit-mitigation-plan.md, point 2, étape 1 ; R-90, SILO-320).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Champ ou type de ligne de trésorerie inconnu. Erreur de requête client sans ambiguïté : 400 Bad Request.
     */
    @ExceptionHandler(UnknownTresorerieFieldException.class)
    public ResponseEntity<ApiErrorResponse> handleUnknownField(UnknownTresorerieFieldException e,
                                                                HttpServletRequest request) {
        LOG.warn("Requête de mise à jour de trésorerie invalide sur {} : {}", request.getRequestURI(), e.getMessage());
        return badRequest(e.getMessage(), request);
    }

    /**
     * Donnée reçue trop mal formée pour être exploitée de façon fiable dans un calcul financier. Erreur de requête client : 400 Bad Request.
     */
    @ExceptionHandler(DataParsingException.class)
    public ResponseEntity<ApiErrorResponse> handleDataParsing(DataParsingException e,
                                                               HttpServletRequest request) {
        LOG.warn("Donnée invalide reçue sur {} : {}", request.getRequestURI(), e.getMessage(), e);
        return badRequest(e.getMessage(), request);
    }

    /**
     * Argument invalide levé explicitement par le code métier. Erreur de requête client : 400 Bad Request.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException e,
                                                                   HttpServletRequest request) {
        LOG.warn("Argument invalide sur {} : {}", request.getRequestURI(), e.getMessage());
        return badRequest(e.getMessage(), request);
    }

    /**
     * Échec de validation Bean Validation (annotations {@code @Valid} sur les DTO générés par OpenAPI).
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
     * Filet de sécurité générique : toute exception non gérée plus spécifiquement finit ici.
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
