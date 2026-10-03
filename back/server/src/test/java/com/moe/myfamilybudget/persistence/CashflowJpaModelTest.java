package com.moe.myfamilybudget.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.moe.myfamilybudget.domain.budget.ChargeModel;
import com.moe.myfamilybudget.domain.budget.IncomeModel;
import com.moe.myfamilybudget.domain.budget.OneOffExpenseModel;
import com.moe.myfamilybudget.domain.budget.TransferModel;
import com.moe.myfamilybudget.domain.budget.VariableIncomeModel;
import com.moe.myfamilybudget.domain.budget.VariableOverrideModel;
import com.moe.myfamilybudget.persistence.converter.CashflowEntityMapper;
import com.moe.myfamilybudget.persistence.repository.CashflowChargeRepository;
import com.moe.myfamilybudget.persistence.repository.CashflowIncomeRepository;
import com.moe.myfamilybudget.persistence.repository.CashflowOneOffRepository;
import com.moe.myfamilybudget.persistence.repository.CashflowTransferRepository;
import com.moe.myfamilybudget.persistence.repository.CashflowVariableIncomeRepository;
import com.moe.myfamilybudget.persistence.repository.CashflowVariableOverrideRepository;

/**
 * DB-1060 : le modele JPA autonome du domaine Tresorerie (additif, sans lien avec le hub) doit restituer
 * revenus, charges, depenses ponctuelles, virements, revenus variables et surcharges a l'identique, dans
 * l'ordre de saisie, sans arrondir les taux.
 */
@DataJpaTest
@DisplayName("CashflowJpaModelTest -- modele JPA Tresorerie autonome (DB-1060)")
class CashflowJpaModelTest {

    @Autowired
    private CashflowIncomeRepository incomeRepository;

    @Autowired
    private CashflowChargeRepository chargeRepository;

    @Autowired
    private CashflowOneOffRepository oneOffRepository;

    @Autowired
    private CashflowTransferRepository transferRepository;

    @Autowired
    private CashflowVariableIncomeRepository variableIncomeRepository;

    @Autowired
    private CashflowVariableOverrideRepository variableOverrideRepository;

    @Autowired
    private TestEntityManager em;

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static void assertSameContent(Object actual, Object expected) {
        assertThat(actual).usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(expected);
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("Revenus : relus a l'identique et dans l'ordre, taux non arrondi, champs optionnels a null, doublons toleres")
    void incomesRoundTrip() {
        List<IncomeModel> models = List.of(
                new IncomeModel("i-2", "Salaire", bd("3200.50"), "2026-01", "2040-12", bd("0.02512345"), "cat-1", "note"),
                new IncomeModel("i-1", "Loyer percu", bd("800.00"), null, null, null, null, null),
                new IncomeModel("i-2", "Doublon", null, null, null, null, null, null));

        incomeRepository.saveAll(CashflowEntityMapper.toIncomeEntities(models));
        flushAndClear();

        List<IncomeModel> reloaded = CashflowEntityMapper.toIncomeModels(incomeRepository.findAllByOrderByPositionAsc());
        assertSameContent(reloaded, models);
        assertThat(reloaded.get(0).growthRate()).isEqualByComparingTo("0.02512345");
        assertThat(reloaded.get(1).growthRate()).isNull();
        assertThat(reloaded).extracting(IncomeModel::label).containsExactly("Salaire", "Loyer percu", "Doublon");
    }

    @Test
    @DisplayName("Charges : taux de croissance absent conserve a null (pas de remplacement par l'inflation)")
    void chargesRoundTrip() {
        List<ChargeModel> models = List.of(
                new ChargeModel("c-1", "Assurance", bd("95.40"), "2026-01", null, bd("0.015"), "cat-2", null),
                new ChargeModel("c-2", "Electricite", bd("120.00"), null, "2035-06", null, null, "estimation"));

        chargeRepository.saveAll(CashflowEntityMapper.toChargeEntities(models));
        flushAndClear();

        List<ChargeModel> reloaded = CashflowEntityMapper.toChargeModels(chargeRepository.findAllByOrderByPositionAsc());
        assertSameContent(reloaded, models);
        assertThat(reloaded.get(1).growthRate()).isNull();
    }

    @Test
    @DisplayName("Depenses ponctuelles et virements relus a l'identique")
    void oneOffsAndTransfersRoundTrip() {
        List<OneOffExpenseModel> oneOffs = List.of(
                new OneOffExpenseModel("o-1", "Voiture", "2027-03-15", bd("18000.00"), "neuve"),
                new OneOffExpenseModel("o-2", "Voyage", null, null, null));
        List<TransferModel> transfers = List.of(
                new TransferModel("t-1", "pl-1", "2026-12-01", bd("5000.00"), null),
                new TransferModel("t-2", null, null, null, "vide"));

        oneOffRepository.saveAll(CashflowEntityMapper.toOneOffEntities(oneOffs));
        transferRepository.saveAll(CashflowEntityMapper.toTransferEntities(transfers));
        flushAndClear();

        assertSameContent(CashflowEntityMapper.toOneOffModels(oneOffRepository.findAllByOrderByPositionAsc()), oneOffs);
        assertSameContent(CashflowEntityMapper.toTransferModels(transferRepository.findAllByOrderByPositionAsc()),
                transfers);
    }

    @Test
    @DisplayName("Revenus variables et surcharges annuelles : taux non arrondi, annees optionnelles, type et notes conserves")
    void variableIncomesAndOverridesRoundTrip() {
        List<VariableIncomeModel> rules = List.of(
                new VariableIncomeModel("v-1", "Prime", "Salaire", bd("0.08333333"), 2026, 2035, "oui", "prime", "n1"),
                new VariableIncomeModel("v-2", "Bonus", null, null, null, null, null, null, null));
        List<VariableOverrideModel> overrides = List.of(
                new VariableOverrideModel("vo-1", "Prime", 2027, bd("4200.00"), "oui", "exceptionnelle"),
                new VariableOverrideModel("vo-2", "Prime", null, null, null, null));

        variableIncomeRepository.saveAll(CashflowEntityMapper.toVariableIncomeEntities(rules));
        variableOverrideRepository.saveAll(CashflowEntityMapper.toVariableOverrideEntities(overrides));
        flushAndClear();

        List<VariableIncomeModel> reloadedRules =
                CashflowEntityMapper.toVariableIncomeModels(variableIncomeRepository.findAllByOrderByPositionAsc());
        assertSameContent(reloadedRules, rules);
        assertThat(reloadedRules.get(0).rate()).isEqualByComparingTo("0.08333333");
        assertSameContent(CashflowEntityMapper.toVariableOverrideModels(
                variableOverrideRepository.findAllByOrderByPositionAsc()), overrides);
    }

    @Test
    @DisplayName("Remplacer le contenu d'une table (suppression, flush, reinsertion) met a jour et reordonne")
    void replacingContentUpdatesAndRemoves() {
        incomeRepository.saveAll(CashflowEntityMapper.toIncomeEntities(List.of(
                new IncomeModel("i-1", "A", bd("1.00"), null, null, null, null, null),
                new IncomeModel("i-2", "B", bd("2.00"), null, null, null, null, null))));
        flushAndClear();

        incomeRepository.deleteAll();
        incomeRepository.flush();
        List<IncomeModel> replacement = List.of(new IncomeModel("i-2", "B2", bd("2.50"), null, null, null, null, null));
        incomeRepository.saveAll(CashflowEntityMapper.toIncomeEntities(replacement));
        flushAndClear();

        assertSameContent(CashflowEntityMapper.toIncomeModels(incomeRepository.findAllByOrderByPositionAsc()),
                replacement);
        assertThat(incomeRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Le mapper tolere null (element et liste)")
    void mapperToleratesNull() {
        assertThat(CashflowEntityMapper.toEntity((IncomeModel) null, 0)).isNull();
        assertThat(CashflowEntityMapper.toEntity((ChargeModel) null, 0)).isNull();
        assertThat(CashflowEntityMapper.toEntity((OneOffExpenseModel) null, 0)).isNull();
        assertThat(CashflowEntityMapper.toEntity((TransferModel) null, 0)).isNull();
        assertThat(CashflowEntityMapper.toEntity((VariableIncomeModel) null, 0)).isNull();
        assertThat(CashflowEntityMapper.toEntity((VariableOverrideModel) null, 0)).isNull();
        assertThat(CashflowEntityMapper.toIncomeEntities(null)).isEmpty();
        assertThat(CashflowEntityMapper.toChargeModels(null)).isEmpty();
        assertThat(CashflowEntityMapper.toTransferEntities(Arrays.asList((TransferModel) null))).isEmpty();
    }
}
