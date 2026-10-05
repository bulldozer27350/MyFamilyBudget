package com.moe.myfamilybudget.domain.bankpointage.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.domain.bankpointage.calculation.BudgetLineProjection;
import com.moe.myfamilybudget.domain.bankpointage.calculation.PointageInput;

class PointageInputTest {

    @Test
    @DisplayName("BudgetLineProjection normalise les valeurs nulles et arrondit à 2 décimales")
    void testBudgetLineProjectionDefaults() {
        BudgetLineProjection empty = new BudgetLineProjection(null, null, null, null, null);
        assertThat(empty.id()).isEmpty();
        assertThat(empty.label()).isEmpty();
        assertThat(empty.kind()).isEqualTo("charge");
        assertThat(empty.monthly()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(empty.categoryId()).isEmpty();

        BudgetLineProjection rounded = new BudgetLineProjection("l1", "Loyer", "charge", new BigDecimal("850.456"), "c1");
        assertThat(rounded.monthly()).isEqualTo(new BigDecimal("850.46"));
    }

    @Test
    @DisplayName("PointageInput remplace les listes et la période nulles par des valeurs vides")
    void testPointageInputDefaults() {
        PointageInput input = new PointageInput(null, null, null, null);

        assertThat(input.transactions()).isEmpty();
        assertThat(input.matchings()).isEmpty();
        assertThat(input.activeBudgetLines()).isEmpty();
        assertThat(input.period().monthISO()).isEmpty();
    }
}
