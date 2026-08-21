package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;

/**
 * Modèle de domaine représentant les résultats détaillés de la projection de retraite pour un individu.
 */
public record RetirementProjectionModel(
    int ageDepart,
    int trimestresValides,
    int trimestresEstimesDepart,
    int trimestresRequis,
    boolean manqueTauxPlein,
    BigDecimal tauxApplique,
    BigDecimal decote,
    BigDecimal surcote,
    BigDecimal sam,
    BigDecimal majoration,
    BigDecimal pensionBaseAnnuelle,
    BigDecimal pointsEstimes,
    BigDecimal valeurPointDepart,
    BigDecimal pensionComplementaireAnnuelle,
    BigDecimal pensionTotaleAnnuelle,
    BigDecimal pensionTotaleMensuelle
) {}
