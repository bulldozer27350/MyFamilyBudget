package com.moe.myfamilybudget.domain.analysis.calculation;

/**
 * Catégorie telle que le silo Analyse la lit (SILO-133) : aucun type du silo Banque/Pointage.
 *
 * @param id           identifiant de la catégorie
 * @param label        libellé affichable
 * @param kind         nature : {@code "Dépense"} (défaut) ou {@code "Revenu"}
 * @param compressible {@code "Oui"} si la dépense est compressible, {@code "Non"} (défaut) sinon
 */
public record AnalysisCategory(String id, String label, String kind, String compressible) {

    public AnalysisCategory {
        if (id == null) id = "";
        if (label == null) label = "";
        if (kind == null) kind = "Dépense";
        if (compressible == null) compressible = "Non";
    }
}
