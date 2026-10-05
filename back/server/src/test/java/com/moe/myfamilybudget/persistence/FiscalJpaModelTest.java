package com.moe.myfamilybudget.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.moe.myfamilybudget.domain.tax.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.domain.tax.model.TaxBracketModel;
import com.moe.myfamilybudget.domain.tax.model.TaxChildModel;
import com.moe.myfamilybudget.domain.tax.model.TaxRateOverrideModel;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalEntityMapper;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalActualOverrideRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalBracketRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalChildRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalRateOverrideRepository;

/**
 * DB-1010 : le modele JPA autonome du domaine Fiscalite (additif, sans lien avec le hub) doit restituer
 * enfants, bareme et surcharges annuelles a l'identique, dans l'ordre de saisie, sans arrondir les taux.
 */
@DataJpaTest
@DisplayName("FiscalJpaModelTest -- modele JPA Fiscalite autonome (DB-1010)")
class FiscalJpaModelTest {

    @Autowired
    private FiscalChildRepository childRepository;

    @Autowired
    private FiscalBracketRepository bracketRepository;

    @Autowired
    private FiscalRateOverrideRepository rateOverrideRepository;

    @Autowired
    private FiscalActualOverrideRepository actualOverrideRepository;

    @Autowired
    private TestEntityManager em;

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("Enfants a charge relus a l'identique, dans l'ordre, annee de naissance optionnelle")
    void childrenRoundTrip() {
        List<TaxChildModel> models = List.of(
                new TaxChildModel("c-2", "Camille", 2015),
                new TaxChildModel("c-1", "Louis", null),
                new TaxChildModel(null, null, 2020));

        childRepository.saveAll(FiscalEntityMapper.toChildEntities(models));
        flushAndClear();

        assertThat(FiscalEntityMapper.toChildModels(childRepository.findAllByOrderByPositionAsc()))
                .isEqualTo(models);
    }

    @Test
    @DisplayName("Tranches du bareme : taux non arrondis, derniere tranche sans plafond, ordre conserve")
    void bracketsRoundTrip() {
        List<TaxBracketModel> models = List.of(
                new TaxBracketModel("b-1", bd("11294.00"), bd("0.00000000")),
                new TaxBracketModel("b-2", bd("28797.00"), bd("0.11000000")),
                new TaxBracketModel("b-3", bd("82341.00"), bd("0.30125000")),
                new TaxBracketModel("b-4", null, bd("0.45000000")));

        bracketRepository.saveAll(FiscalEntityMapper.toBracketEntities(models));
        flushAndClear();

        List<TaxBracketModel> reloaded =
                FiscalEntityMapper.toBracketModels(bracketRepository.findAllByOrderByPositionAsc());
        assertThat(reloaded).isEqualTo(models);
        assertThat(reloaded.get(2).rate()).isEqualByComparingTo("0.30125");
        assertThat(reloaded.get(3).upTo()).isNull();
    }

    @Test
    @DisplayName("Surcharges annuelles (taux, impot reel) relues a l'identique, doublons d'annee toleres")
    void yearlyOverridesRoundTrip() {
        List<TaxRateOverrideModel> rates = List.of(
                new TaxRateOverrideModel(2027, bd("0.05000000")),
                new TaxRateOverrideModel(2026, bd("0.12500000")),
                new TaxRateOverrideModel(2026, bd("0.13000000")));
        List<TaxActualOverrideModel> actuals = List.of(
                new TaxActualOverrideModel(2026, bd("1800.00")),
                new TaxActualOverrideModel(2025, bd("1650.50")));

        rateOverrideRepository.saveAll(FiscalEntityMapper.toRateOverrideEntities(rates));
        actualOverrideRepository.saveAll(FiscalEntityMapper.toActualOverrideEntities(actuals));
        flushAndClear();

        assertThat(FiscalEntityMapper.toRateOverrideModels(rateOverrideRepository.findAllByOrderByPositionAsc()))
                .isEqualTo(rates);
        assertThat(FiscalEntityMapper.toActualOverrideModels(actualOverrideRepository.findAllByOrderByPositionAsc()))
                .isEqualTo(actuals);
    }

    @Test
    @DisplayName("Remplacer le contenu d'une table (suppression, flush, reinsertion) met a jour et reordonne")
    void replacingContentUpdatesAndRemoves() {
        childRepository.saveAll(FiscalEntityMapper.toChildEntities(List.of(
                new TaxChildModel("c-1", "Louis", 2012), new TaxChildModel("c-2", "Camille", 2015))));
        flushAndClear();

        childRepository.deleteAll();
        childRepository.flush();
        List<TaxChildModel> replacement = List.of(new TaxChildModel("c-2", "Camille", 2016));
        childRepository.saveAll(FiscalEntityMapper.toChildEntities(replacement));
        flushAndClear();

        assertThat(FiscalEntityMapper.toChildModels(childRepository.findAllByOrderByPositionAsc()))
                .isEqualTo(replacement);
        assertThat(childRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Le mapper tolere null (element et liste)")
    void mapperToleratesNull() {
        assertThat(FiscalEntityMapper.toEntity((TaxChildModel) null, 0)).isNull();
        assertThat(FiscalEntityMapper.toEntity((TaxBracketModel) null, 0)).isNull();
        assertThat(FiscalEntityMapper.toEntity((TaxRateOverrideModel) null, 0)).isNull();
        assertThat(FiscalEntityMapper.toEntity((TaxActualOverrideModel) null, 0)).isNull();
        assertThat(FiscalEntityMapper.toChildEntities(null)).isEmpty();
        assertThat(FiscalEntityMapper.toBracketModels(null)).isEmpty();
        assertThat(FiscalEntityMapper.toRateOverrideEntities(java.util.Arrays.asList((TaxRateOverrideModel) null)))
                .isEmpty();
    }
}
