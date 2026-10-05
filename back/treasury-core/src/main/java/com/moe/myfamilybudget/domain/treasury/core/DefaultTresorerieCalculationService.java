package com.moe.myfamilybudget.domain.treasury.core;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.moe.myfamilybudget.domain.treasury.calculation.ChargeProjectionInput;
import com.moe.myfamilybudget.domain.treasury.calculation.IncomeProjectionInput;
import com.moe.myfamilybudget.domain.treasury.calculation.OneOffCashflow;
import com.moe.myfamilybudget.domain.treasury.calculation.TransferProjection;
import com.moe.myfamilybudget.domain.treasury.calculation.TresorerieCalculationService;
import com.moe.myfamilybudget.domain.treasury.calculation.TreasuryPensionProjection;
import com.moe.myfamilybudget.domain.treasury.calculation.TreasuryPlacementCashflow;
import com.moe.myfamilybudget.domain.treasury.calculation.TreasuryProjection;
import com.moe.myfamilybudget.domain.treasury.calculation.TreasuryProjectionInput;
import com.moe.myfamilybudget.domain.treasury.calculation.TreasuryTaxProjection;
import com.moe.myfamilybudget.domain.treasury.calculation.VariableIncomeProjection;
import com.moe.myfamilybudget.domain.treasury.model.CashflowYearModel;
import com.moe.myfamilybudget.domain.treasury.model.VariablePreviewCellModel;
import com.moe.myfamilybudget.domain.treasury.model.VariablePreviewModel;

/**
 * Calcul pur de la trésorerie prévisionnelle (Trésorerie) : projections de flux annuel et aperçu
 * des revenus variables.
 *
 * <p>SILO-153 : implémentation de {@link TresorerieCalculationService} ({@code treasury-api}), hébergée dans
 * {@code treasury-core} ; les fonctions unitaires publiques deviennent des méthodes d'instance de l'interface.
 *
 * <p>RF-401 (voir doc/architecture/06-domaine-tresorerie.md) : ce moteur ne dépend plus de
 * {@code BudgetDataModel}. Il consomme uniquement {@link TreasuryProjectionInput} préparé par
 * {@code TreasuryInputFactory} et produit une {@link TreasuryProjection}.
 *
 * <p>Les fonctions unitaires {@link #chargeMonthlyForYear}, {@link #chargeAnnualForYear},
 * {@link #incomeMonthlyForYear} et {@link #incomeAnnualForYear} sont extraites en fonctions
 * pures opérant exclusivement sur leurs modèles minimaux ({@link ChargeProjectionInput},
 * {@link IncomeProjectionInput}).
 */
public class DefaultTresorerieCalculationService implements TresorerieCalculationService {

    @Override
    public TreasuryProjection compute(TreasuryProjectionInput input) {
        Objects.requireNonNull(input, "input");

        int startYear = input.period().startYear();
        int endYear = input.period().endYear();

        List<Integer> years = new ArrayList<>();
        for (int y = startYear; y <= endYear; y++) {
            years.add(y);
        }

        Map<Integer, BigDecimal> pensionByYear = input.retirementIncome().years().stream()
                .collect(Collectors.toMap(
                        TreasuryPensionProjection.AnnualPension::year,
                        TreasuryPensionProjection.AnnualPension::amount,
                        BigDecimal::add));

        Map<Integer, BigDecimal> placementByYear = input.placements().stream()
                .collect(Collectors.toMap(
                        TreasuryPlacementCashflow::year,
                        TreasuryPlacementCashflow::amount,
                        BigDecimal::add));

        Map<Integer, TreasuryTaxProjection.Withholding> taxByYear = input.taxProjection().years().stream()
                .collect(Collectors.toMap(
                        TreasuryTaxProjection.Withholding::year,
                        w -> w,
                        (a, b) -> b));

        BigDecimal balance = input.parameters().startBalance();
        BigDecimal inflationRate = input.parameters().inflationRate();

        List<CashflowYearModel> cashflow = new ArrayList<>();
        for (int idx = 0; idx < years.size(); idx++) {
            int year = years.get(idx);

            BigDecimal regularIncome = BigDecimal.ZERO;
            for (IncomeProjectionInput i : input.incomes()) {
                regularIncome = regularIncome.add(incomeAnnualForYear(i, year));
            }
            BigDecimal pension = pensionByYear.getOrDefault(year, BigDecimal.ZERO);
            BigDecimal income = regularIncome.add(pension);

            BigDecimal variableIncome = computeVariableIncomeForYear(input.incomes(), input.variableIncomes(), year);
            BigDecimal savings = placementByYear.getOrDefault(year, BigDecimal.ZERO);

            BigDecimal charges = BigDecimal.ZERO;
            for (ChargeProjectionInput c : input.charges()) {
                charges = charges.add(chargeAnnualForYear(c, year, inflationRate));
            }

            BigDecimal oneoff = BigDecimal.ZERO;
            for (OneOffCashflow o : input.oneOffExpenses()) {
                if (o.date() != null && o.date().getYear() == year) {
                    oneoff = oneoff.add(o.amount());
                }
            }

            BigDecimal transfersY = BigDecimal.ZERO;
            for (TransferProjection t : input.transfers()) {
                if (t.date() != null && t.date().getYear() == year) {
                    transfersY = transfersY.add(t.amount());
                }
            }

            TreasuryTaxProjection.Withholding taxInfo = taxByYear.get(year);
            BigDecimal impots = taxInfo != null ? taxInfo.withheld() : BigDecimal.ZERO;

            BigDecimal regularisation = BigDecimal.ZERO;
            if (idx > 0) {
                int prevYear = years.get(idx - 1);
                TreasuryTaxProjection.Withholding prevTax = taxByYear.get(prevYear);
                if (prevTax != null) {
                    regularisation = prevTax.actual().subtract(prevTax.withheld());
                }
            }

            BigDecimal net = income.add(variableIncome)
                    .subtract(savings)
                    .subtract(charges)
                    .subtract(oneoff)
                    .add(transfersY)
                    .subtract(impots)
                    .subtract(regularisation);

            balance = balance.add(net);

            cashflow.add(new CashflowYearModel(
                    year, income, variableIncome, savings, charges, oneoff, transfersY, impots, regularisation, net, balance
            ));
        }

        List<Integer> previewYears = years.stream().limit(4).collect(Collectors.toList());
        List<VariablePreviewModel> variablePreview = computeVariablePreview(input.incomes(), input.variableIncomes(), previewYears);

        return new TreasuryProjection(years, cashflow, variablePreview, previewYears);
    }

    private BigDecimal computeVariableIncomeForYear(
            List<IncomeProjectionInput> incomes,
            List<VariableIncomeProjection> variableIncomes,
            int year) {
        BigDecimal sum = BigDecimal.ZERO;
        for (VariableIncomeProjection v : variableIncomes) {
            int sY = v.startYear() != null ? v.startYear() : 0;
            int eY = v.endYear() != null ? v.endYear() : 9999;
            if (year < sY || year > eY) continue;

            IncomeProjectionInput refRow = incomes.stream()
                    .filter(r -> Objects.equals(r.label(), v.refIncomeLabel()))
                    .findFirst()
                    .orElse(null);

            BigDecimal refAnnual = refRow != null ? incomeAnnualForYear(refRow, year) : BigDecimal.ZERO;
            BigDecimal forecast = refAnnual.multiply(v.rate());

            VariableIncomeProjection.Override override = v.overrides().stream()
                    .filter(o -> o.year() == year)
                    .findFirst()
                    .orElse(null);

            sum = sum.add(override != null ? override.amount() : forecast);
        }
        return sum;
    }

    private List<VariablePreviewModel> computeVariablePreview(
            List<IncomeProjectionInput> incomes,
            List<VariableIncomeProjection> variableIncomes,
            List<Integer> previewYears) {
        List<VariablePreviewModel> variablePreview = new ArrayList<>();

        for (VariableIncomeProjection v : variableIncomes) {
            IncomeProjectionInput refRow = incomes.stream()
                    .filter(r -> Objects.equals(r.label(), v.refIncomeLabel()))
                    .findFirst()
                    .orElse(null);

            List<VariablePreviewCellModel> cells = new ArrayList<>();
            for (int y : previewYears) {
                int sY = v.startYear() != null ? v.startYear() : 0;
                int eY = v.endYear() != null ? v.endYear() : 9999;
                if (y < sY || y > eY) {
                    cells.add(new VariablePreviewCellModel(y, null, false));
                    continue;
                }

                BigDecimal refAnnual = refRow != null ? incomeAnnualForYear(refRow, y) : BigDecimal.ZERO;
                BigDecimal forecast = refAnnual.multiply(v.rate());

                VariableIncomeProjection.Override override = v.overrides().stream()
                        .filter(o -> o.year() == y)
                        .findFirst()
                        .orElse(null);

                BigDecimal amt = override != null ? override.amount() : forecast;
                boolean isReal = override != null;
                cells.add(new VariablePreviewCellModel(y, amt, isReal));
            }
            variablePreview.add(new VariablePreviewModel(v.label(), cells));
        }

        return variablePreview;
    }

    // --- Fonctions de calcul unitaires (RF-401, SILO-153) ---

    @Override
    public BigDecimal chargeMonthlyForYear(ChargeProjectionInput c, int year, BigDecimal inflationRate) {
        if (c == null || c.start() == null || c.end() == null || year < c.start().getYear() || year > c.end().getYear()) {
            return BigDecimal.ZERO;
        }
        BigDecimal growth = chargeEffectiveGrowth(c.growthRate(), inflationRate);
        int elapsed = Math.max(0, year - c.start().getYear());
        double factor = Math.pow(1.0 + growth.doubleValue(), elapsed);
        return c.monthly().multiply(BigDecimal.valueOf(factor));
    }

    @Override
    public BigDecimal chargeAnnualForYear(ChargeProjectionInput c, int year, BigDecimal inflationRate) {
        if (c == null) return BigDecimal.ZERO;
        int m = monthsActiveInYear(c.start(), c.end(), year);
        if (m == 0) return BigDecimal.ZERO;
        int sY = c.start() != null ? c.start().getYear() : year;
        BigDecimal growth = chargeEffectiveGrowth(c.growthRate(), inflationRate);
        int elapsed = Math.max(0, year - sY);
        double factor = Math.pow(1.0 + growth.doubleValue(), elapsed);
        return c.monthly().multiply(BigDecimal.valueOf(factor)).multiply(BigDecimal.valueOf(m));
    }

    public static BigDecimal chargeEffectiveGrowth(BigDecimal growthRate, BigDecimal inflationRate) {
        if (growthRate != null && BigDecimal.ZERO.compareTo(growthRate) != 0) {
            return growthRate;
        }
        return inflationRate != null ? inflationRate : new BigDecimal("0.015");
    }

    @Override
    public BigDecimal incomeMonthlyForYear(IncomeProjectionInput i, int year) {
        if (i == null || i.start() == null || i.end() == null || year < i.start().getYear() || year > i.end().getYear()) {
            return BigDecimal.ZERO;
        }
        int elapsed = Math.max(0, year - i.start().getYear());
        double factor = Math.pow(1.0 + i.growthRate().doubleValue(), elapsed);
        return i.monthly().multiply(BigDecimal.valueOf(factor));
    }

    @Override
    public BigDecimal incomeAnnualForYear(IncomeProjectionInput i, int year) {
        if (i == null) return BigDecimal.ZERO;
        int m = monthsActiveInYear(i.start(), i.end(), year);
        if (m == 0) return BigDecimal.ZERO;
        int sY = i.start() != null ? i.start().getYear() : year;
        int elapsed = Math.max(0, year - sY);
        double factor = Math.pow(1.0 + i.growthRate().doubleValue(), elapsed);
        return i.monthly().multiply(BigDecimal.valueOf(factor)).multiply(BigDecimal.valueOf(m));
    }

    public static int monthsActiveInYear(LocalDate start, LocalDate end, int year) {
        if (start == null || end == null) return 0;
        LocalDate yStart = LocalDate.of(year, 1, 1);
        LocalDate yEnd = LocalDate.of(year, 12, 31);
        LocalDate s = start.isAfter(yStart) ? start : yStart;
        LocalDate e = end.isBefore(yEnd) ? end : yEnd;
        if (e.isBefore(s)) return 0;
        return (e.getYear() - s.getYear()) * 12 + (e.getMonthValue() - s.getMonthValue()) + 1;
    }
}
