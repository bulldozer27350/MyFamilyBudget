package com.moe.myfamilybudget.server.internal.factory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.server.internal.calculation.AnnualSalaryProjection;
import com.moe.myfamilybudget.server.internal.calculation.RetirementCalculationInput;
import com.moe.myfamilybudget.server.internal.calculation.RetirementParameters;
import com.moe.myfamilybudget.server.internal.calculation.RetirementPersonInput;
import com.moe.myfamilybudget.server.internal.calculation.SalaryHistoryEntry;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.domain.budget.IncomeModel;
import com.moe.myfamilybudget.server.internal.model.RetirementModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;

@Component
public class RetirementInputFactory {

    public RetirementCalculationInput create(BudgetDataModel data) {
        BudgetDataModel effectiveData = data != null ? data : new BudgetDataModel(
            null, null, null, null, null, null, null, null, null, null, null, null, null, null, null
        );
        SettingsModel settings = effectiveData.getEffectiveSettings();
        RetirementModel retirement = effectiveData.retirement();
        int retireYear = settings.getEffectiveBirthYear() + settings.getEffectiveRetireAge();

        RetirementParameters parameters = new RetirementParameters(
            retirement != null ? retirement.pass2026() : null,
            retirement != null ? retirement.passGrowthRate() : null,
            retirement != null ? retirement.agircPointValue() : null,
            parseDate(retirement != null ? retirement.agircPointDateGlobal() : null),
            retirement != null ? retirement.agircPointGrowthRate() : null
        );

        List<RetirementPersonInput> people = retirement == null ? List.of() : retirement.getEffectivePeople().stream()
            .map(person -> toPersonInput(effectiveData, settings, person, retireYear))
            .toList();

        return new RetirementCalculationInput(
            retireYear,
            parameters,
            effectiveData.getEffectiveTaxChildren().size(),
            people
        );
    }

    private RetirementPersonInput toPersonInput(
        BudgetDataModel data,
        SettingsModel settings,
        RetirementModel.RetirementPersonModel person,
        int retireYear
    ) {
        int birthYear = person.birthYear() != null ? person.birthYear() : settings.getEffectiveBirthYear();
        LocalDate trimestresDate = parseDate(person.trimestresDate());
        if (trimestresDate == null) {
            trimestresDate = LocalDate.of(LocalDate.now().getYear() - 1, 1, 1);
        }

        int projectionStartYear = trimestresDate.getYear() + 1;
        List<AnnualSalaryProjection> projectedSalaries = projectionStartYear > retireYear
            ? List.of()
            : IntStream.rangeClosed(projectionStartYear, retireYear)
                .mapToObj(year -> new AnnualSalaryProjection(year, projectedAnnualSalary(data, person.incomeLabel(), year)))
                .toList();

        List<SalaryHistoryEntry> salaryHistory = person.getEffectiveSalaryHistory().stream()
            .filter(entry -> entry.year() != null)
            .map(entry -> new SalaryHistoryEntry(entry.year(), entry.getEffectiveSalary()))
            .toList();

        return new RetirementPersonInput(
            birthYear,
            person.getEffectiveTrimestresValides(),
            trimestresDate,
            salaryHistory,
            person.getEffectiveAgircPoints(),
            person.getEffectiveRatioPointsParEuro(),
            projectedSalaries
        );
    }

    private BigDecimal projectedAnnualSalary(BudgetDataModel data, String incomeLabel, int year) {
        if (incomeLabel == null || incomeLabel.isBlank()) return BigDecimal.ZERO;
        return data.getEffectiveIncomes().stream()
            .filter(income -> incomeLabel.equalsIgnoreCase(income.label()))
            .findFirst()
            .map(income -> incomeAnnualForYear(income, year))
            .orElse(BigDecimal.ZERO);
    }

    private BigDecimal incomeAnnualForYear(IncomeModel income, int year) {
        Integer startYear = yearOf(income.start());
        if (startYear == null) startYear = year;
        int yearsElapsed = Math.max(0, year - startYear);
        double growthFactor = Math.pow(1.0 + income.getEffectiveGrowthRate().doubleValue(), yearsElapsed);
        BigDecimal effectiveMonthly = income.getEffectiveMonthly().multiply(BigDecimal.valueOf(growthFactor));
        return effectiveMonthly.multiply(BigDecimal.valueOf(monthsActiveInYear(income.start(), income.end(), year)));
    }

    private int monthsActiveInYear(String startISO, String endISO, int year) {
        if (startISO == null || endISO == null || startISO.isBlank() || endISO.isBlank()) return 0;
        try {
            LocalDate start = LocalDate.parse(startISO);
            LocalDate end = LocalDate.parse(endISO);
            LocalDate yearStart = LocalDate.of(year, 1, 1);
            LocalDate yearEnd = LocalDate.of(year, 12, 31);
            LocalDate effectiveStart = start.isAfter(yearStart) ? start : yearStart;
            LocalDate effectiveEnd = end.isBefore(yearEnd) ? end : yearEnd;
            if (effectiveEnd.isBefore(effectiveStart)) return 0;
            return (effectiveEnd.getYear() - effectiveStart.getYear()) * 12
                + (effectiveEnd.getMonthValue() - effectiveStart.getMonthValue()) + 1;
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException ex) {
            try {
                return LocalDate.of(Integer.parseInt(value.substring(0, 4)), 1, 1);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }

    private Integer yearOf(String value) {
        LocalDate date = parseDate(value);
        return date != null ? date.getYear() : null;
    }
}
