package com.moe.myfamilybudget.server.internal.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.moe.myfamilybudget.server.internal.persistence.entity.LoanEntity;
import com.moe.myfamilybudget.server.internal.persistence.entity.PlacementEntity;
import com.moe.myfamilybudget.server.internal.persistence.entity.RealEstateEntity;

/**
 * Les taux sont stockes en fractions (0,0251 = 2,51 %). Sans precision explicite, Hibernate cree
 * des colonnes NUMERIC(38,2) et arrondit 0,0251 a 0,03 : un taux de pret « 2,5 % » revenait « 3 % »
 * apres rechargement. Ces tests relisent la valeur depuis la base (cache de premier niveau vide).
 */
@DataJpaTest
@DisplayName("RatePrecisionPersistenceTest -- les taux ne sont pas arrondis en base")
class RatePrecisionPersistenceTest {

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("Un taux de pret a decimales est relu a l'identique")
    void loanRateKeepsDecimals() {
        LoanEntity loan = new LoanEntity("loan-1", "Pret immo", new BigDecimal("150000"),
                new BigDecimal("0.02512"), new BigDecimal("900"), new BigDecimal("30"),
                "2026-01-01", "2040-01-01");

        Long id = em.persistAndFlush(loan).getId();
        em.clear();

        assertThat(em.find(LoanEntity.class, id).getRate()).isEqualByComparingTo("0.02512");
    }

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

    @Test
    @DisplayName("Le taux de revalorisation immobilier est relu a l'identique")
    void realEstateGrowthRateKeepsDecimals() {
        RealEstateEntity realEstate = new RealEstateEntity("re-1", "Maison", "RP",
                new BigDecimal("300000"), 2026, new BigDecimal("0.0175"), "");

        Long id = em.persistAndFlush(realEstate).getId();
        em.clear();

        assertThat(em.find(RealEstateEntity.class, id).getAnnualGrowthRate()).isEqualByComparingTo("0.0175");
    }
}
