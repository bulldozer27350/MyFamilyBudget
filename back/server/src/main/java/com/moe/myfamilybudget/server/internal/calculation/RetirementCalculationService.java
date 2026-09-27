package com.moe.myfamilybudget.server.internal.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.server.internal.model.RetirementProjection;
import com.moe.myfamilybudget.server.internal.model.RetirementProjectionModel;

/**
 * Moteur de calcul retraite centralisé (RF-101, voir doc/architecture/03-domaine-retraite.md).
 *
 * Unique propriétaire du calcul de projection retraite backend : {@code RetraiteServiceImpl}
 * délègue désormais entièrement à ce moteur, qui devient la version canonique du calcul
 * auparavant dupliqué à l'identique dans {@code RetraiteServiceImpl},
 * {@code OverviewCalculationService} et {@code TresorerieCalculationService}. Ces deux derniers
 * ne sont pas encore branchés dessus (voir RF-102).
 *
 * Reçoit exclusivement {@link RetirementCalculationInput} : aucun {@code BudgetDataModel}, aucun
 * autre modèle persistant, conformément au garde-fou ArchUnit RF-001
 * (doc/architecture/00-principes.md).
 */
@Component
public class RetirementCalculationService {

    private static final int TRIMESTRES_REQUIS = 172;
    private static final int AGE_TAUX_PLEIN_AUTO = 67;
    private static final BigDecimal DECOTE_PAR_TRIMESTRE = new BigDecimal("0.00625");
    private static final BigDecimal SURCOTE_PAR_TRIMESTRE = new BigDecimal("0.0125");
    private static final BigDecimal TAUX_PLEIN = new BigDecimal("0.50");
    private static final BigDecimal TAUX_MINORE_PLANCHER = new BigDecimal("0.375");
    private static final BigDecimal MAJORATION_3_ENFANTS = new BigDecimal("0.10");

    public RetirementProjection compute(RetirementCalculationInput input) {
        List<RetirementProjectionModel> people = input.people().stream()
            .map(person -> computePerson(person, input.retireYear(), input.parameters(), input.eligibleChildrenCount()))
            .toList();
        return new RetirementProjection(people);
    }

    private RetirementProjectionModel computePerson(
        RetirementPersonInput person,
        int retireYear,
        RetirementParameters parameters,
        int eligibleChildrenCount
    ) {
        int ageDepart = retireYear - person.birthYear();
        int trimestresValides = person.trimestresValides();

        int trimestresFuturs = (int) person.projectedSalaries().stream()
            .filter(salary -> salary.annualSalary().compareTo(BigDecimal.ZERO) > 0)
            .count() * 4;

        int trimestresEstimesDepart = trimestresValides + trimestresFuturs;
        int trimestresJusquTauxPleinAuto = Math.max(0, (AGE_TAUX_PLEIN_AUTO - ageDepart) * 4);

        BigDecimal tauxApplique = TAUX_PLEIN;
        BigDecimal decote = BigDecimal.ZERO;
        BigDecimal surcote = BigDecimal.ZERO;

        if (trimestresEstimesDepart < TRIMESTRES_REQUIS) {
            int manquants = TRIMESTRES_REQUIS - trimestresEstimesDepart;
            int trimestresDecote = Math.min(manquants, trimestresJusquTauxPleinAuto);
            decote = DECOTE_PAR_TRIMESTRE.multiply(BigDecimal.valueOf(trimestresDecote));
            tauxApplique = TAUX_PLEIN.subtract(decote).max(TAUX_MINORE_PLANCHER);
        } else if (trimestresEstimesDepart > TRIMESTRES_REQUIS) {
            int surplus = trimestresEstimesDepart - TRIMESTRES_REQUIS;
            surcote = SURCOTE_PAR_TRIMESTRE.multiply(BigDecimal.valueOf(surplus));
            tauxApplique = TAUX_PLEIN.add(surcote);
        }

        Map<Integer, BigDecimal> byYear = new HashMap<>();
        for (SalaryHistoryEntry entry : person.salaryHistory()) {
            if (entry.salary().compareTo(BigDecimal.ZERO) > 0) {
                byYear.put(entry.year(), entry.salary());
            }
        }

        List<AnnualSalaryProjection> futureYears = new ArrayList<>();
        for (AnnualSalaryProjection projected : person.projectedSalaries()) {
            if (projected.year() >= retireYear) {
                continue;
            }
            if (projected.annualSalary().compareTo(BigDecimal.ZERO) > 0) {
                futureYears.add(projected);
                byYear.putIfAbsent(projected.year(), projected.annualSalary());
            }
        }

        List<Map.Entry<Integer, BigDecimal>> allEntries = new ArrayList<>(byYear.entrySet());
        allEntries.sort((a, b) -> Integer.compare(b.getKey(), a.getKey()));
        List<Map.Entry<Integer, BigDecimal>> last25 = allEntries.subList(0, Math.min(25, allEntries.size()));

        BigDecimal sumCapped = BigDecimal.ZERO;
        for (Map.Entry<Integer, BigDecimal> entry : last25) {
            BigDecimal pass = passForYear(parameters, entry.getKey());
            sumCapped = sumCapped.add(entry.getValue().min(pass));
        }

        BigDecimal sam = last25.isEmpty()
            ? BigDecimal.ZERO
            : sumCapped.divide(BigDecimal.valueOf(last25.size()), 10, RoundingMode.HALF_UP);
        BigDecimal majoration = eligibleChildrenCount >= 3 ? BigDecimal.ONE.add(MAJORATION_3_ENFANTS) : BigDecimal.ONE;

        double ratioTrimestresVal = Math.min(trimestresEstimesDepart, TRIMESTRES_REQUIS) / (double) TRIMESTRES_REQUIS;
        BigDecimal ratioTrimestres = BigDecimal.valueOf(ratioTrimestresVal);

        BigDecimal pensionBaseAnnuelle = sam.multiply(tauxApplique).multiply(ratioTrimestres).multiply(majoration);

        BigDecimal pointsActuels = person.agircPoints();
        BigDecimal ratioPointsParEuro = person.ratioPointsParEuro();

        BigDecimal pointsFuturs = BigDecimal.ZERO;
        for (AnnualSalaryProjection futureYear : futureYears) {
            pointsFuturs = pointsFuturs.add(futureYear.annualSalary().multiply(ratioPointsParEuro));
        }

        BigDecimal pointsEstimes = pointsActuels.add(pointsFuturs);
        BigDecimal valeurPointDepart = agircPointValueForYear(parameters, retireYear);
        BigDecimal pensionComplementaireAnnuelle = pointsEstimes.multiply(valeurPointDepart).multiply(majoration);

        BigDecimal pensionTotaleAnnuelle = pensionBaseAnnuelle.add(pensionComplementaireAnnuelle);
        BigDecimal pensionTotaleMensuelle = pensionTotaleAnnuelle.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);

        return new RetirementProjectionModel(
            ageDepart,
            trimestresValides,
            trimestresEstimesDepart,
            TRIMESTRES_REQUIS,
            trimestresEstimesDepart < TRIMESTRES_REQUIS,
            tauxApplique,
            decote,
            surcote,
            sam,
            majoration,
            pensionBaseAnnuelle,
            pointsEstimes,
            valeurPointDepart,
            pensionComplementaireAnnuelle,
            pensionTotaleAnnuelle,
            pensionTotaleMensuelle
        );
    }

    private BigDecimal passForYear(RetirementParameters parameters, int year) {
        double factor = Math.pow(1.0 + parameters.effectivePassGrowthRate().doubleValue(), year - 2026);
        return parameters.effectivePass2026().multiply(BigDecimal.valueOf(factor));
    }

    private BigDecimal agircPointValueForYear(RetirementParameters parameters, int year) {
        int baseYear = parameters.effectiveAgircPointDate().getYear();
        int elapsed = Math.max(0, year - baseYear);
        double factor = Math.pow(1.0 + parameters.effectiveAgircPointGrowthRate().doubleValue(), elapsed);
        return parameters.effectiveAgircPointValue().multiply(BigDecimal.valueOf(factor));
    }
}
