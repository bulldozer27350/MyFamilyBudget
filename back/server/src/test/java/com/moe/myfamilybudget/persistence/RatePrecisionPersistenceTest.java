package com.moe.myfamilybudget.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.moe.myfamilybudget.persistence.entity.PlacementEntity;

/**
 * Les taux sont stockes en fractions (0,0251 = 2,51 %). Sans precision explicite, Hibernate cree
 * des colonnes NUMERIC(38,2) et arrondit 0,0251 a 0,03 : un taux de pret « 2,5 % » revenait « 3 % »
 * apres rechargement. Ces tests relisent la valeur depuis la base (cache de premier niveau vide). Le taux des prets
 * est couvert par {@code CreditLoanJpaModelTest} (table {@code credit_loan}, SILO-214). R-30b : le taux de
 * revalorisation immobilier (ex-{@code RealEstateEntity}, retiree) est desormais couvert par
 * {@code WealthJpaModelTest} (table {@code wealth_real_estate}, SILO-215).
 */
@DataJpaTest
@DisplayName("RatePrecisionPersistenceTest -- les taux ne sont pas arrondis en base")
class RatePrecisionPersistenceTest {

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("Les trois taux d'un placement sont relus a l'identique")
    void placementRatesKeepDecimals() {
        PlacementEntity placement = new PlacementEntity("pl-1", "Livret", "cash",
                new BigDecimal("1000"), "2026-01-01", BigDecimal.ZERO, null, null,
                new BigDecimal("0.0125"), new BigDecimal("0.0275"), new BigDecimal("0.03125"),
                Boolean.FALSE, "");

        Long id = em.persistAndFlush(placement).getId();
        em.clear();

        PlacementEntity reloaded = em.find(PlacementEntity.class, id);
        assertThat(reloaded.getRatePess()).isEqualByComparingTo("0.0125");
        assertThat(reloaded.getRateCorr()).isEqualByComparingTo("0.0275");
        assertThat(reloaded.getRateOpti()).isEqualByComparingTo("0.03125");
    }
}
