package com.moe.myfamilybudget.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.moe.myfamilybudget.domain.credit.model.LoanModel;
import com.moe.myfamilybudget.persistence.converter.CreditLoanEntityMapper;
import com.moe.myfamilybudget.persistence.entity.CreditLoanEntity;
import com.moe.myfamilybudget.persistence.repository.CreditLoanRepository;

/**
 * DB-1040 : le modele JPA autonome du domaine Credit (additif, sans lien avec le hub) doit restituer
 * les prets a l'identique, dans l'ordre de saisie, sans arrondir les taux.
 */
@DataJpaTest
@DisplayName("CreditLoanJpaModelTest -- modele JPA Credit autonome (DB-1040)")
class CreditLoanJpaModelTest {

    @Autowired
    private CreditLoanRepository repository;

    @Autowired
    private TestEntityManager em;

    private static LoanModel contractLoan(String id, String label, String rate) {
        return new LoanModel(id, label, new BigDecimal("170000.00"), new BigDecimal(rate),
                new BigDecimal("825.00"), new BigDecimal("25.00"), "2026-08-05", "2046-01-05",
                new BigDecimal("180000.00"), 240, "2036-01-05");
    }

    private List<LoanModel> saveAndReload(List<LoanModel> models) {
        repository.saveAll(CreditLoanEntityMapper.toEntities(models));
        em.flush();
        em.clear();
        return CreditLoanEntityMapper.toModels(repository.findAllByOrderByPositionAsc());
    }

    @Test
    @DisplayName("Prets avec informations du contrat relus a l'identique et dans l'ordre de saisie")
    void roundTripKeepsContentAndOrder() {
        List<LoanModel> models = List.of(
                contractLoan("loan-2", "Pret B", "0.02250000"),
                contractLoan("loan-1", "Pret A", "0.01000000"),
                contractLoan("loan-3", "Pret C", "0.03125000"));

        assertThat(saveAndReload(models)).isEqualTo(models);
    }

    @Test
    @DisplayName("Un taux a decimales n'est pas arrondi (0,02512 relu 0,02512)")
    void rateKeepsDecimals() {
        List<LoanModel> reloaded = saveAndReload(List.of(contractLoan("loan-1", "Pret", "0.02512000")));

        assertThat(reloaded.get(0).rate()).isEqualByComparingTo("0.02512");
    }

    @Test
    @DisplayName("Les champs du contrat absents (constructeur historique) restent null")
    void optionalContractFieldsStayNull() {
        LoanModel legacy = new LoanModel("loan-legacy", "Ancien", new BigDecimal("50000.00"),
                new BigDecimal("0.01000000"), new BigDecimal("500.00"), BigDecimal.ZERO.setScale(2),
                "2026-01-01", "2036-01-01");
        LoanModel sparse = new LoanModel("loan-sparse", null, null, null, null, null, null, null);

        List<LoanModel> reloaded = saveAndReload(List.of(legacy, sparse));

        assertThat(reloaded.get(0)).isEqualTo(legacy);
        assertThat(reloaded.get(0).initialAmount()).isNull();
        assertThat(reloaded.get(0).totalInstallments()).isNull();
        assertThat(reloaded.get(0).stepDate()).isNull();
        assertThat(reloaded.get(1)).isEqualTo(sparse);
    }

    @Test
    @DisplayName("Remplacer la liste met a jour, supprime et reordonne les prets")
    void replacingListUpdatesAndRemoves() {
        saveAndReload(List.of(contractLoan("loan-1", "Pret A", "0.01000000"),
                contractLoan("loan-2", "Pret B", "0.02000000")));

        repository.deleteAll();
        em.flush();
        LoanModel updated = contractLoan("loan-2", "Pret B renegocie", "0.01500000");
        List<LoanModel> reloaded = saveAndReload(List.of(updated));

        assertThat(reloaded).containsExactly(updated);
        assertThat(repository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Le mapper refuse un pret sans identifiant et ignore null")
    void mapperHandlesNullAndMissingId() {
        assertThat(CreditLoanEntityMapper.toEntity(null, 0)).isNull();
        assertThat(CreditLoanEntityMapper.toModel(null)).isNull();
        assertThat(CreditLoanEntityMapper.toEntities(null)).isEmpty();
        assertThatThrownBy(() -> CreditLoanEntityMapper.toEntity(contractLoan(null, "Sans id", "0.01000000"), 0))
                .isInstanceOf(NullPointerException.class);
        CreditLoanEntity entity = CreditLoanEntityMapper.toEntity(contractLoan("loan-1", "x", "0.01000000"), 3);
        assertThat(entity.getPosition()).isEqualTo(3);
    }
}
