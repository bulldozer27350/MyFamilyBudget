package com.moe.myfamilybudget.server.internal.calculation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Contrat d'entrée du moteur d'analyse des prêts (RF-800, voir
 * doc/architecture/10-domaine-prets-suggestions.md).
 *
 * <p>Simple encapsulation de frontière : le calcul lui-même n'est pas modifié. L'assemblage —
 * extraction des prêts, résolution des buckets des placements en alternatives, choix du taux de
 * marché — est une responsabilité de l'appelant.
 *
 * @param loans        prêts à analyser
 * @param alternatives placements candidats comme alternative au remboursement anticipé
 * @param parameters   hypothèses de l'analyse
 * @param marketRate   taux de marché résolu par l'appelant (requête, saisie manuelle ou source
 *                     automatique), {@code null} si inconnu ; prime sur {@code parameters.marketRate()}
 * @param today        date de référence des projections (injectée pour des calculs reproductibles)
 */
public record LoanAdviceInput(
        List<LoanInput> loans,
        List<LiquidPlacementAlternative> alternatives,
        LoanAdviceParameters parameters,
        BigDecimal marketRate,
        LocalDate today) {

    public LoanAdviceInput {
        if (loans == null) loans = Collections.emptyList();
        if (alternatives == null) alternatives = Collections.emptyList();
        if (parameters == null) parameters = LoanAdviceParameters.defaults(null);
        Objects.requireNonNull(today, "today");
    }
}
