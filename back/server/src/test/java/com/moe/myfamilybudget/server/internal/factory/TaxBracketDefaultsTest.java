package com.moe.myfamilybudget.server.internal.factory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.application.factory.TaxBracketDefaults;
import com.moe.myfamilybudget.domain.tax.model.TaxBracketModel;

/**
 * SILO-111 : le barème par défaut reprend la règle de l'ancien {@code BudgetDataModel}.
 */
class TaxBracketDefaultsTest {

    @Test
    @DisplayName("orDefault() : barème par défaut en 5 tranches quand rien n'est configuré (null ou vide)")
    void testDefaultsWhenNothingConfigured() {
        assertThat(TaxBracketDefaults.orDefault(null)).hasSize(5);
        List<TaxBracketModel> defaults = TaxBracketDefaults.orDefault(List.of());

        assertThat(defaults).hasSize(5);
        assertThat(defaults.get(0).upTo()).isEqualByComparingTo("11294");
        assertThat(defaults.get(0).rate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(defaults.get(4).upTo()).isNull();
        assertThat(defaults.get(4).rate()).isEqualByComparingTo("0.45");
    }

    @Test
    @DisplayName("orDefault() : une configuration existante est conservée telle quelle")
    void testConfiguredBracketsAreKept() {
        List<TaxBracketModel> configured = List.of(new TaxBracketModel("tb1", null, new BigDecimal("0.20")));

        assertThat(TaxBracketDefaults.orDefault(configured)).isSameAs(configured);
    }
}
