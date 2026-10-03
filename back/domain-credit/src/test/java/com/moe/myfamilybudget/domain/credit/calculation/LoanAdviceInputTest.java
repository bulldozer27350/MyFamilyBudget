package com.moe.myfamilybudget.domain.credit.calculation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoanAdviceInputTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);

    @Test
    @DisplayName("LoanAdviceInput remplace listes et paramètres nuls par des valeurs vides / par défaut")
    void testDefaults() {
        LoanAdviceInput input = new LoanAdviceInput(null, null, null, null, TODAY);

        assertThat(input.loans()).isEmpty();
        assertThat(input.alternatives()).isEmpty();
        assertThat(input.parameters()).isEqualTo(LoanAdviceParameters.defaults(null));
        assertThat(input.marketRate()).isNull();
        assertThat(input.today()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("LoanAdviceInput exige une date de référence explicite")
    void testTodayIsRequired() {
        assertThatNullPointerException()
                .isThrownBy(() -> new LoanAdviceInput(null, null, null, null, null));
    }

    @Test
    @DisplayName("LiquidPlacementAlternative normalise libellé et bucket nuls")
    void testAlternativeDefaults() {
        LiquidPlacementAlternative alt = new LiquidPlacementAlternative(null, null, null);

        assertThat(alt.label()).isEmpty();
        assertThat(alt.bucket()).isEmpty();
        assertThat(alt.rateCorr()).isNull();
    }
}
