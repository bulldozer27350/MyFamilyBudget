package com.moe.myfamilybudget.server.internal.error;

/**
 * Exception métier levée lorsqu'une donnée reçue (champ de formulaire, ligne de CSV, fichier
 * importé...) est mal formée au point de ne pas pouvoir être exploitée, et que l'impact d'une
 * conversion silencieuse vers une valeur par défaut serait trop risqué pour être acceptable
 * (typiquement : import bancaire, calculs fiscaux).
 *
 * Remplace, pour ces cas précis, le pattern {@code catch (Exception e) { return null; }} identifié
 * par l'audit (point 2) : plutôt que de laisser une valeur par défaut se propager silencieusement
 * dans un calcul financier, on remonte une erreur explicite. {@link GlobalExceptionHandler} la
 * traduit en réponse HTTP 400 avec un message exploitable côté client.
 *
 * Pour les cas de formatage d'affichage à faible enjeu (ex. une date non reconnue dans un libellé
 * de relevé bancaire), on continue de préférer la journalisation (log.warn) suivie d'un retour de
 * valeur par défaut documentée, sans lever cette exception : voir le plan de mitigation, point 2,
 * étape 2.
 */
public class DataParsingException extends RuntimeException {

    public DataParsingException(String message) {
        super(message);
    }

    public DataParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
