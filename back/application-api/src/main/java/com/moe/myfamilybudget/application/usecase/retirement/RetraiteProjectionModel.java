package com.moe.myfamilybudget.application.usecase.retirement;

import java.math.BigDecimal;

/** Résultats détaillés de la projection de retraite d'un individu. */
public record RetraiteProjectionModel(
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
