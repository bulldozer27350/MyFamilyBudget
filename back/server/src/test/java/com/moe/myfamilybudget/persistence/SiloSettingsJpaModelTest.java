package com.moe.myfamilybudget.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.moe.myfamilybudget.domain.retirement.core.persistence.PensionSettingsMapper;
import com.moe.myfamilybudget.domain.retirement.core.persistence.PensionSettingsRepository;
import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalSettingsMapper;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalSettingsRepository;
import com.moe.myfamilybudget.domain.tax.model.TaxSettingsModel;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowSettingsMapper;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowSettingsRepository;
import com.moe.myfamilybudget.domain.treasury.model.TresorerieSettingsModel;

/**
 * SILO-220 (lot A) : les paramètres persistés chez leurs propriétaires (Retraite, Fiscalité, Trésorerie) sont
 * restitués à l'identique, sans défaut appliqué (une valeur absente reste {@code null}), et sans arrondir
 * l'abattement. Additif : la table historique {@code settings} n'est pas touchée.
 */
@DataJpaTest
@DisplayName("SiloSettingsJpaModelTest -- paramètres chez leurs propriétaires (SILO-220, lot A)")
class SiloSettingsJpaModelTest {

    @Autowired
    private PensionSettingsRepository pensionRepository;

    @Autowired
    private FiscalSettingsRepository fiscalRepository;

    @Autowired
    private CashflowSettingsRepository cashflowRepository;

    @Autowired
    private TestEntityManager em;

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private RetirementSettingsModel reloadRetirement(RetirementSettingsModel model) {
        pensionRepository.deleteAll();
        pensionRepository.flush();
        pensionRepository.save(PensionSettingsMapper.toEntity(model));
        em.flush();
        em.clear();
        return PensionSettingsMapper.toModel(pensionRepository.findFirstByOrderByIdAsc().orElseThrow());
    }

    private TaxSettingsModel reloadTax(TaxSettingsModel model) {
        fiscalRepository.deleteAll();
        fiscalRepository.flush();
        fiscalRepository.save(FiscalSettingsMapper.toEntity(model));
        em.flush();
        em.clear();
        return FiscalSettingsMapper.toModel(fiscalRepository.findFirstByOrderByIdAsc().orElseThrow());
    }

    private TresorerieSettingsModel reloadTresorerie(TresorerieSettingsModel model) {
        cashflowRepository.deleteAll();
        cashflowRepository.flush();
        cashflowRepository.save(CashflowSettingsMapper.toEntity(model));
        em.flush();
        em.clear();
        return CashflowSettingsMapper.toModel(cashflowRepository.findFirstByOrderByIdAsc().orElseThrow());
    }

    @Test
    @DisplayName("Retraite : année de naissance et âge de départ relus à l'identique")
    void retirementSettingsRoundTrip() {
        RetirementSettingsModel reloaded = reloadRetirement(new RetirementSettingsModel(1982, 62));

        assertThat(reloaded).isEqualTo(new RetirementSettingsModel(1982, 62));
    }

    @Test
    @DisplayName("Retraite : valeurs absentes conservées nulles (aucun défaut appliqué)")
    void retirementSettingsKeepNulls() {
        RetirementSettingsModel reloaded = reloadRetirement(new RetirementSettingsModel(null, null));

        assertThat(reloaded.birthYear()).isNull();
        assertThat(reloaded.retireAge()).isNull();
    }

    @Test
    @DisplayName("Fiscalité : âge de sortie des enfants et abattement (8 décimales) relus à l'identique")
    void taxSettingsRoundTrip() {
        TaxSettingsModel reloaded = reloadTax(new TaxSettingsModel(23, bd("0.10123457")));

        assertThat(reloaded.childExitAge()).isEqualTo(23);
        assertThat(reloaded.taxAbattement()).isEqualByComparingTo(bd("0.10123457"));
    }

    @Test
    @DisplayName("Fiscalité : valeurs absentes conservées nulles (aucun défaut appliqué)")
    void taxSettingsKeepNulls() {
        TaxSettingsModel reloaded = reloadTax(new TaxSettingsModel(null, null));

        assertThat(reloaded.childExitAge()).isNull();
        assertThat(reloaded.taxAbattement()).isNull();
    }

    @Test
    @DisplayName("Trésorerie : pivot, solde de départ, sweep et seuils relus à l'identique")
    void tresorerieSettingsRoundTrip() {
        TresorerieSettingsModel reloaded = reloadTresorerie(new TresorerieSettingsModel(
                "2026-10-01", "auto", bd("1234.56"), Boolean.TRUE, bd("5000.00"), bd("300.00"), bd("150.00")));

        assertThat(reloaded.pivotDate()).isEqualTo("2026-10-01");
        assertThat(reloaded.pivotMode()).isEqualTo("auto");
        assertThat(reloaded.startBalance()).isEqualByComparingTo(bd("1234.56"));
        assertThat(reloaded.sweepEnabled()).isTrue();
        assertThat(reloaded.cashCeiling()).isEqualByComparingTo(bd("5000.00"));
        assertThat(reloaded.cashFloor()).isEqualByComparingTo(bd("300.00"));
        assertThat(reloaded.cashAlertThreshold()).isEqualByComparingTo(bd("150.00"));
    }

    @Test
    @DisplayName("Trésorerie : valeurs absentes conservées nulles (aucun défaut appliqué)")
    void tresorerieSettingsKeepNulls() {
        TresorerieSettingsModel reloaded = reloadTresorerie(
                new TresorerieSettingsModel(null, null, null, null, null, null, null));

        assertThat(reloaded.pivotDate()).isNull();
        assertThat(reloaded.pivotMode()).isNull();
        assertThat(reloaded.startBalance()).isNull();
        assertThat(reloaded.sweepEnabled()).isNull();
        assertThat(reloaded.cashCeiling()).isNull();
        assertThat(reloaded.cashFloor()).isNull();
        assertThat(reloaded.cashAlertThreshold()).isNull();
    }

    @Test
    @DisplayName("Un modèle null est converti en null, dans les deux sens")
    void nullModelsAreNull() {
        assertThat(PensionSettingsMapper.toEntity(null)).isNull();
        assertThat(PensionSettingsMapper.toModel(null)).isNull();
        assertThat(FiscalSettingsMapper.toEntity(null)).isNull();
        assertThat(FiscalSettingsMapper.toModel(null)).isNull();
        assertThat(CashflowSettingsMapper.toEntity(null)).isNull();
        assertThat(CashflowSettingsMapper.toModel(null)).isNull();
    }
}
