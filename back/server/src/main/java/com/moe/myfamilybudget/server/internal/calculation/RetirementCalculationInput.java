package com.moe.myfamilybudget.server.internal.calculation;

import java.util.List;

public record RetirementCalculationInput(
    int retireYear,
    RetirementParameters parameters,
    int eligibleChildrenCount,
    List<RetirementPersonInput> people
) {
    public RetirementCalculationInput {
        parameters = parameters != null ? parameters : new RetirementParameters(null, null, null, null, null);
        people = people != null ? List.copyOf(people) : List.of();
    }
}
