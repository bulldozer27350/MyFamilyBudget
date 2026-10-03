package com.moe.myfamilybudget.server.internal.factory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.moe.myfamilybudget.domain.bankpointage.calculation.BudgetLineProjection;
import com.moe.myfamilybudget.domain.bankpointage.calculation.PointageInput;
import com.moe.myfamilybudget.domain.bankpointage.calculation.PointagePeriod;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.domain.budget.ChargeModel;
import com.moe.myfamilybudget.domain.budget.IncomeModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;

/**
 * Construit un {@link PointageInput} et les {@link BudgetLineProjection} qu'il transporte (RF-501,
 * voir doc/architecture/07-domaine-banque-pointage.md).
 *
 * <p>Le calcul des lignes budgétaires actives d'un mois (ex-{@code
 * PointageCalculator.calculateActiveBudgetLines}) est une étape de composition en amont : c'est
 * ici, et non plus dans le moteur de pointage, que {@code ChargeModel}, {@code IncomeModel},
 * {@code PlacementModel} et {@code SettingsModel} sont traduits en {@link BudgetLineProjection}.
 * Les règles (bornes de dates, croissance annuelle, taux d'inflation par défaut, libellé
 * « Épargne : » des placements) sont reprises à l'identique de l'ancienne implémentation.
 *
 * <p>Cette classe vit dans {@code internal.factory}, hors du package {@code internal.calculation}
 * gardé par ArchUnit. Elle est aussi la source des lignes actives pour Analyse tant que RF-601 n'a
 * pas déplacé cette composition en amont d'{@code AnalyseCalculator}.
 */
public final class PointageInputFactory {

    private static final Logger LOG = LoggerFactory.getLogger(PointageInputFactory.class);

    private static final BigDecimal FALLBACK_CHARGE_GROWTH_RATE = new BigDecimal("0.015");

    private PointageInputFactory() {
    }

    /**
     * Assemble l'entrée de pointage d'un mois.
     *
     * @param bankImport import bancaire courant (transactions et rapprochements)
     * @param data       budget courant, utilisé uniquement pour composer les lignes actives
     * @param monthISO   mois pointé au format {@code YYYY-MM}
     */
    public static PointageInput from(BankImportModel bankImport, BudgetDataModel data, String monthISO) {
        Objects.requireNonNull(bankImport, "bankImport");
        Objects.requireNonNull(data, "data");

        List<BankImportModel.MatchingLinkModel> monthLinks = Collections.emptyList();
        if (bankImport.matchings() != null && monthISO != null) {
            for (BankImportModel.MatchingModel m : bankImport.matchings()) {
                if (m != null && monthISO.equals(m.month()) && m.links() != null) {
                    monthLinks = m.links();
                    break;
                }
            }
        }

        return new PointageInput(
                bankImport.transactions(),
                monthLinks,
                activeBudgetLines(data.charges(), data.incomes(), data.placements(), data.settings(), monthISO),
                new PointagePeriod(monthISO));
    }

    /**
     * Extrait les lignes de budget actives pour un mois donné (format {@code YYYY-MM}).
     */
    public static List<BudgetLineProjection> activeBudgetLines(
            List<ChargeModel> charges,
            List<IncomeModel> incomes,
            List<PlacementModel> placements,
            SettingsModel settings,
            String monthISO) {

        if (monthISO == null || monthISO.isBlank()) {
            return Collections.emptyList();
        }

        int year = parseYearFromMonthISO(monthISO);
        SettingsModel effectiveSettings = settings != null
                ? settings
                : new SettingsModel(null, null, null, null, null, null, null, null, null, null, null, null);
        BigDecimal inflationRate = effectiveSettings.getEffectiveInflationRate();

        List<BudgetLineProjection> activeLines = new ArrayList<>();

        // 1. Charges
        if (charges != null) {
            for (ChargeModel c : charges) {
                if (c == null) continue;
                if (!isActive(monthISO, c.start(), c.end())) continue;

                BigDecimal monthly = calculateChargeMonthly(c, year, inflationRate);
                if (monthly.compareTo(BigDecimal.ZERO) > 0) {
                    activeLines.add(new BudgetLineProjection(c.id(), c.label(), "charge", monthly, c.categoryId()));
                }
            }
        }

        // 2. Incomes (Revenus)
        if (incomes != null) {
            for (IncomeModel inc : incomes) {
                if (inc == null) continue;
                if (!isActive(monthISO, inc.start(), inc.end())) continue;

                BigDecimal monthly = calculateIncomeMonthly(inc, year);
                if (monthly.compareTo(BigDecimal.ZERO) > 0) {
                    activeLines.add(new BudgetLineProjection(inc.id(), inc.label(), "revenu", monthly, inc.categoryId()));
                }
            }
        }

        // 3. Placements (Épargne)
        if (placements != null) {
            for (PlacementModel p : placements) {
                if (p == null) continue;
                BigDecimal m = p.monthly() != null ? p.monthly() : BigDecimal.ZERO;
                if (m.compareTo(BigDecimal.ZERO) <= 0) continue;

                if (!isActive(monthISO, p.monthlyFrom(), p.monthlyUntil())) continue;

                activeLines.add(new BudgetLineProjection(p.id(), "Épargne : " + p.label(), "placement", m, p.category()));
            }
        }

        return activeLines;
    }

    // --- Helpers de composition internes (repris de l'ancien PointageCalculator) ---

    private static boolean isActive(String monthISO, String start, String end) {
        boolean startOK = start == null || start.isBlank() || monthISO.compareTo(toMonthISO(start)) >= 0;
        boolean endOK = end == null || end.isBlank() || monthISO.compareTo(toMonthISO(end)) <= 0;
        return startOK && endOK;
    }

    private static int parseYearFromMonthISO(String monthISO) {
        try {
            return Integer.parseInt(monthISO.substring(0, 4));
        } catch (Exception e) {
            LOG.warn("Mois ISO illisible, année 2026 utilisée par défaut : '{}'", monthISO, e);
            return 2026;
        }
    }

    private static String toMonthISO(String dateStr) {
        if (dateStr == null || dateStr.length() < 7) return "";
        return dateStr.substring(0, 7);
    }

    private static BigDecimal calculateChargeMonthly(ChargeModel c, int year, BigDecimal inflationRate) {
        Integer sY = c.start() != null && !c.start().isBlank() ? parseYearFromMonthISO(c.start()) : null;
        Integer eY = c.end() != null && !c.end().isBlank() ? parseYearFromMonthISO(c.end()) : null;
        if (sY != null && eY != null && (year < sY || year > eY)) {
            return BigDecimal.ZERO;
        }

        BigDecimal growth = (c.growthRate() != null && BigDecimal.ZERO.compareTo(c.growthRate()) != 0)
                ? c.growthRate()
                : (inflationRate != null ? inflationRate : FALLBACK_CHARGE_GROWTH_RATE);

        int elapsed = sY != null ? Math.max(0, year - sY) : 0;
        double factor = Math.pow(1.0 + growth.doubleValue(), elapsed);

        BigDecimal baseMonthly = c.getEffectiveMonthly();
        return baseMonthly.multiply(BigDecimal.valueOf(factor)).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal calculateIncomeMonthly(IncomeModel inc, int year) {
        Integer sY = inc.start() != null && !inc.start().isBlank() ? parseYearFromMonthISO(inc.start()) : null;
        Integer eY = inc.end() != null && !inc.end().isBlank() ? parseYearFromMonthISO(inc.end()) : null;
        if (sY != null && eY != null && (year < sY || year > eY)) {
            return BigDecimal.ZERO;
        }

        int elapsed = sY != null ? Math.max(0, year - sY) : 0;
        double factor = Math.pow(1.0 + inc.getEffectiveGrowthRate().doubleValue(), elapsed);

        BigDecimal baseMonthly = inc.getEffectiveMonthly();
        return baseMonthly.multiply(BigDecimal.valueOf(factor)).setScale(2, RoundingMode.HALF_UP);
    }
}
