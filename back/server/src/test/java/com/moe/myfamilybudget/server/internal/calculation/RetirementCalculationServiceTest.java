package com.moe.myfamilybudget.server.internal.calculation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.server.internal.model.RetirementProjection;
import com.moe.myfamilybudget.server.internal.model.RetirementProjectionModel;

/**
 * Tests de composant du moteur retraite centralisé (RF-103, voir
 * doc/architecture/03-domaine-retraite.md) : uniquement {@link RetirementCalculationInput},
 * aucun {@code BudgetDataModel}, aucun contexte Spring.
 */
class RetirementCalculationServiceTest {

    private static final RetirementParameters DEFAULT_PARAMETERS = new RetirementParameters(null, null, null, null, null);
    private static final LocalDate TRIMESTRES_DATE = LocalDate.of(2025, 1, 1);

    private final RetirementCalculationService service = new RetirementCalculationService();

    private static RetirementPersonInput person(int birthYear, int trimestresValides, BigDecimal agircPoints) {
        return person(birthYear, trimestresValides, agircPoints, List.of(), List.of());
    }

    private static RetirementPersonInput person(
        int birthYear,
        int trimestresValides,
        BigDecimal agircPoints,
        List<SalaryHistoryEntry> history,
        List<AnnualSalaryProjection> projected
    ) {
        return new RetirementPersonInput(
            birthYear, trimestresValides, TRIMESTRES_DATE, history, agircPoints, new BigDecimal("0.0051"), projected
        );
    }

    private static List<SalaryHistoryEntry> history(int fromYear, int toYear, String salary) {
        List<SalaryHistoryEntry> entries = new ArrayList<>();
        for (int year = fromYear; year <= toYear; year++) {
            entries.add(new SalaryHistoryEntry(year, new BigDecimal(salary)));
        }
        return entries;
    }

    private RetirementProjectionModel computeOne(
        int retireYear, RetirementParameters parameters, int children, RetirementPersonInput person
    ) {
        RetirementProjection projection = service.compute(
            new RetirementCalculationInput(retireYear, parameters, children, List.of(person))
        );
        assertThat(projection.people()).hasSize(1);
        return projection.people().get(0);
    }

    @Test
    @DisplayName("Aucune personne en entrée => aucune projection")
    void noPeople() {
        RetirementProjection projection = service.compute(
            new RetirementCalculationInput(2049, DEFAULT_PARAMETERS, 0, List.of())
        );

        assertThat(projection.people()).isEmpty();
    }

    @Test
    @DisplayName("L'âge de départ est retireYear - birthYear")
    void ageDepart() {
        RetirementProjectionModel result = computeOne(2049, DEFAULT_PARAMETERS, 0, person(1985, 172, BigDecimal.ZERO));

        assertThat(result.ageDepart()).isEqualTo(64);
        assertThat(result.trimestresValides()).isEqualTo(172);
        assertThat(result.trimestresRequis()).isEqualTo(172);
    }

    @Test
    @DisplayName("Décote plafonnée aux trimestres restant jusqu'à 67 ans")
    void decoteCappedByAgeTauxPleinAuto() {
        // 64 ans au départ => 3 ans * 4 = 12 trimestres max de décote, même avec 52 trimestres manquants
        RetirementProjectionModel result = computeOne(2049, DEFAULT_PARAMETERS, 0, person(1985, 120, new BigDecimal("1000")));

        assertThat(result.manqueTauxPlein()).isTrue();
        assertThat(result.decote()).isEqualByComparingTo("0.075");
        assertThat(result.surcote()).isEqualByComparingTo("0");
        assertThat(result.tauxApplique()).isEqualByComparingTo("0.425");
    }

    @Test
    @DisplayName("Le taux minoré ne descend jamais sous 37,5 %")
    void tauxMinoreFloor() {
        // 60 ans au départ => 28 trimestres de décote possibles : 0,50 - 0,175 = 0,325 => plancher 0,375
        RetirementProjectionModel result = computeOne(2045, DEFAULT_PARAMETERS, 0, person(1985, 0, BigDecimal.ZERO));

        assertThat(result.decote()).isEqualByComparingTo("0.175");
        assertThat(result.tauxApplique()).isEqualByComparingTo("0.375");
    }

    @Test
    @DisplayName("Surcote de 1,25 % par trimestre au-delà de 172")
    void surcote() {
        RetirementProjectionModel result = computeOne(2034, DEFAULT_PARAMETERS, 0, person(1970, 180, new BigDecimal("3000")));

        assertThat(result.manqueTauxPlein()).isFalse();
        assertThat(result.surcote()).isEqualByComparingTo("0.10");
        assertThat(result.decote()).isEqualByComparingTo("0");
        assertThat(result.tauxApplique()).isEqualByComparingTo("0.60");
    }

    @Test
    @DisplayName("Exactement 172 trimestres => taux plein sans décote ni surcote")
    void tauxPlein() {
        RetirementProjectionModel result = computeOne(2049, DEFAULT_PARAMETERS, 0, person(1985, 172, BigDecimal.ZERO));

        assertThat(result.manqueTauxPlein()).isFalse();
        assertThat(result.decote()).isEqualByComparingTo("0");
        assertThat(result.surcote()).isEqualByComparingTo("0");
        assertThat(result.tauxApplique()).isEqualByComparingTo("0.50");
    }

    @Test
    @DisplayName("Chaque année projetée avec un salaire > 0 ajoute 4 trimestres, y compris l'année de départ")
    void futureTrimestres() {
        List<AnnualSalaryProjection> projected = List.of(
            new AnnualSalaryProjection(2046, new BigDecimal("40000")),
            new AnnualSalaryProjection(2047, BigDecimal.ZERO),
            new AnnualSalaryProjection(2048, new BigDecimal("40000")),
            new AnnualSalaryProjection(2049, new BigDecimal("40000"))
        );

        RetirementProjectionModel result = computeOne(
            2049, DEFAULT_PARAMETERS, 0, person(1985, 100, BigDecimal.ZERO, List.of(), projected)
        );

        assertThat(result.trimestresEstimesDepart()).isEqualTo(112);
    }

    @Test
    @DisplayName("Majoration de 10 % à partir de 3 enfants, aucune en dessous")
    void majoration() {
        RetirementPersonInput person = person(1980, 172, new BigDecimal("2000"));

        assertThat(computeOne(2044, DEFAULT_PARAMETERS, 3, person).majoration()).isEqualByComparingTo("1.10");
        assertThat(computeOne(2044, DEFAULT_PARAMETERS, 4, person).majoration()).isEqualByComparingTo("1.10");
        assertThat(computeOne(2044, DEFAULT_PARAMETERS, 2, person).majoration()).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("SAM et pension de base à taux plein sur 25 années d'historique")
    void samAndBasePension() {
        RetirementPersonInput person = person(1985, 172, BigDecimal.ZERO, history(2000, 2024, "30000"), List.of());

        RetirementProjectionModel result = computeOne(2049, DEFAULT_PARAMETERS, 0, person);

        assertThat(result.sam()).isEqualByComparingTo("30000");
        assertThat(result.pensionBaseAnnuelle()).isEqualByComparingTo("15000");
    }

    @Test
    @DisplayName("La pension de base est proratisée par les trimestres et minorée par la décote")
    void basePensionProratedAndDiscounted() {
        // 86 / 172 = 0,5 ; décote 12 trimestres => taux 0,425 ; 30000 * 0,425 * 0,5 = 6375
        RetirementPersonInput person = person(1985, 86, BigDecimal.ZERO, history(2000, 2024, "30000"), List.of());

        RetirementProjectionModel result = computeOne(2049, DEFAULT_PARAMETERS, 0, person);

        assertThat(result.pensionBaseAnnuelle()).isEqualByComparingTo("6375");
    }

    @Test
    @DisplayName("Les salaires sont plafonnés au PASS de l'année")
    void salaryCappedAtPass() {
        RetirementPersonInput person = person(
            1985, 172, BigDecimal.ZERO, List.of(new SalaryHistoryEntry(2026, new BigDecimal("100000"))), List.of()
        );

        RetirementProjectionModel result = computeOne(2049, DEFAULT_PARAMETERS, 0, person);

        assertThat(result.sam()).isEqualByComparingTo("47100");
    }

    @Test
    @DisplayName("Seules les 25 années les plus récentes entrent dans le SAM")
    void samKeepsLast25Years() {
        List<SalaryHistoryEntry> entries = new ArrayList<>(history(1995, 1999, "10000"));
        entries.addAll(history(2000, 2024, "30000"));

        RetirementProjectionModel result = computeOne(
            2049, DEFAULT_PARAMETERS, 0, person(1985, 172, BigDecimal.ZERO, entries, List.of())
        );

        assertThat(result.sam()).isEqualByComparingTo("30000");
    }

    @Test
    @DisplayName("Les années d'historique à salaire nul sont ignorées")
    void zeroSalaryHistoryIgnored() {
        List<SalaryHistoryEntry> entries = new ArrayList<>(history(2020, 2024, "30000"));
        entries.add(new SalaryHistoryEntry(2019, BigDecimal.ZERO));

        RetirementProjectionModel result = computeOne(
            2049, DEFAULT_PARAMETERS, 0, person(1985, 172, BigDecimal.ZERO, entries, List.of())
        );

        assertThat(result.sam()).isEqualByComparingTo("30000");
    }

    @Test
    @DisplayName("Les points futurs excluent l'année de départ et utilisent le ratio points/euro")
    void futurePointsExcludeRetireYear() {
        List<AnnualSalaryProjection> projected = List.of(
            new AnnualSalaryProjection(2048, new BigDecimal("10000")),
            new AnnualSalaryProjection(2049, new BigDecimal("20000"))
        );

        RetirementProjectionModel result = computeOne(
            2049, DEFAULT_PARAMETERS, 0, person(1985, 172, BigDecimal.ZERO, List.of(), projected)
        );

        // 10000 * 0,0051 = 51 points (l'année 2049 ne génère pas de points)
        assertThat(result.pointsEstimes()).isEqualByComparingTo("51");
    }

    @Test
    @DisplayName("Pension complémentaire = points * valeur du point revalorisée * majoration")
    void complementaryPension() {
        RetirementProjectionModel result = computeOne(
            2049, DEFAULT_PARAMETERS, 0, person(1985, 172, new BigDecimal("1000"))
        );

        // valeur du point 2025 (défaut 1,4386) revalorisée de 1 % sur 24 ans
        assertThat(result.valeurPointDepart().doubleValue()).isCloseTo(1.8266402653, within(1e-6));
        assertThat(result.pensionComplementaireAnnuelle().doubleValue()).isCloseTo(1826.6402653, within(1e-4));
    }

    @Test
    @DisplayName("La valeur du point suit les paramètres fournis en entrée")
    void agircPointValueFollowsParameters() {
        RetirementParameters parameters = new RetirementParameters(
            null, null, new BigDecimal("2.0"), LocalDate.of(2030, 1, 1), new BigDecimal("0.02")
        );

        RetirementProjectionModel result = computeOne(2032, parameters, 0, person(1985, 172, BigDecimal.ZERO));

        assertThat(result.valeurPointDepart().doubleValue()).isCloseTo(2.0808, within(1e-6));
    }

    @Test
    @DisplayName("Pension totale = base + complémentaire, mensuelle = annuelle / 12")
    void totals() {
        RetirementPersonInput person = person(1985, 172, new BigDecimal("1000"), history(2000, 2024, "30000"), List.of());

        RetirementProjectionModel result = computeOne(2049, DEFAULT_PARAMETERS, 0, person);

        assertThat(result.pensionTotaleAnnuelle().doubleValue()).isCloseTo(16826.6402653, within(1e-4));
        assertThat(result.pensionTotaleAnnuelle())
            .isEqualByComparingTo(result.pensionBaseAnnuelle().add(result.pensionComplementaireAnnuelle()));
        assertThat(result.pensionTotaleMensuelle().doubleValue()).isCloseTo(16826.6402653 / 12, within(1e-4));
    }

    @Test
    @DisplayName("Plusieurs personnes : une projection par personne, dans l'ordre de l'entrée")
    void multiplePeopleKeepOrder() {
        RetirementPersonInput first = person(1985, 172, BigDecimal.ZERO);
        RetirementPersonInput second = person(1970, 180, BigDecimal.ZERO);

        RetirementProjection projection = service.compute(
            new RetirementCalculationInput(2049, DEFAULT_PARAMETERS, 0, List.of(first, second))
        );

        assertThat(projection.people()).hasSize(2);
        assertThat(projection.people().get(0).ageDepart()).isEqualTo(64);
        assertThat(projection.people().get(1).ageDepart()).isEqualTo(79);
        assertThat(projection.people().get(1).trimestresValides()).isEqualTo(180);
    }
}
