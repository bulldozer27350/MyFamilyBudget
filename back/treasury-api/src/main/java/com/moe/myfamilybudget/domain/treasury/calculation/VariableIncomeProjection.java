package com.moe.myfamilybudget.domain.treasury.calculation;

import java.math.BigDecimal;
import java.util.List;

/**
 * Revenu variable (prime, bonus...) exprimé en pourcentage d'un revenu de référence, avec ses
 * montants réels saisis par année (RF-400, voir doc/architecture/06-domaine-tresorerie.md).
 *
 * <p>Projection minimale de {@code VariableIncomeModel} + {@code VariableOverrideModel} : le
 * rapprochement par {@code refIncomeLabel} avec {@link IncomeProjectionInput#label()} reste fait
 * par le moteur, comme aujourd'hui par label sur {@code IncomeModel}.
 *
 * @param label          libellé du revenu variable
 * @param refIncomeLabel libellé du revenu régulier de référence pour le calcul de la prévision
 * @param startYear      première année active, {@code null} si sans borne basse
 * @param endYear        dernière année active, {@code null} si sans borne haute
 * @param rate           taux appliqué au revenu de référence pour la prévision ({@code null} vaut 0)
 * @param taxable        {@code "Non"} si ce revenu variable n'est pas imposable par défaut ; toute
 *                        autre valeur (y compris {@code null}) vaut imposable
 * @param overrides      montants réels saisis, qui remplacent la prévision pour l'année concernée
 */
public record VariableIncomeProjection(
        String label,
        String refIncomeLabel,
        Integer startYear,
        Integer endYear,
        BigDecimal rate,
        String taxable,
        List<Override> overrides) {

    public VariableIncomeProjection {
        rate = rate != null ? rate : BigDecimal.ZERO;
        overrides = overrides != null ? List.copyOf(overrides) : List.of();
    }

    /**
     * Montant réel saisi pour une année donnée, qui remplace la prévision.
     *
     * @param year    année concernée
     * @param amount  montant réel ({@code null} vaut 0)
     * @param taxable {@code "Non"} si ce montant n'est pas imposable ; {@code null} ou vide pour
     *                reprendre le caractère imposable par défaut du revenu variable
     */
    public record Override(int year, BigDecimal amount, String taxable) {
        public Override {
            amount = amount != null ? amount : BigDecimal.ZERO;
        }
    }
}
