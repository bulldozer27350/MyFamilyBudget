package com.moe.myfamilybudget.server.internal.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.BankImportModel.BankColumnMappingModel;
import com.moe.myfamilybudget.server.internal.model.BankImportModel.BankImportRuleModel;
import com.moe.myfamilybudget.server.internal.model.BankImportModel.BankTransactionModel;
import com.moe.myfamilybudget.server.internal.model.BankImportModel.BankTransactionSplitModel;
import com.moe.myfamilybudget.server.internal.model.BankImportModel.CategoryModel;
import com.moe.myfamilybudget.server.internal.model.BankImportModel.MatchingLinkModel;
import com.moe.myfamilybudget.server.internal.model.BankImportModel.MatchingModel;
import com.moe.myfamilybudget.server.internal.model.BankImportModel.PendingOperationModel;
import com.moe.myfamilybudget.server.internal.persistence.converter.BankImportDocumentMapper;
import com.moe.myfamilybudget.server.internal.persistence.entity.BankImportDocumentEntity;
import com.moe.myfamilybudget.server.internal.persistence.repository.BankImportDocumentRepository;

/**
 * DB-1030 : le modele JPA autonome du domaine Banque (additif, sans lien avec le hub) doit restituer l'import
 * bancaire a l'identique, y compris au-dela de la taille d'une colonne texte courte.
 */
@DataJpaTest
@DisplayName("BankImportDocumentJpaModelTest -- modele JPA Banque autonome (DB-1030)")
class BankImportDocumentJpaModelTest {

    @Autowired
    private BankImportDocumentRepository repository;

    @Autowired
    private TestEntityManager em;

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static BankImportModel sampleImport() {
        return new BankImportModel(
                new BankColumnMappingModel(",", "YYYY-MM-DD", false, 0, 1, 2, 3),
                List.of(new CategoryModel("cat-1", "Courses d\u00e9taill\u00e9es", "D\u00e9pense", "Oui"),
                        new CategoryModel("cat-2", "Salaire", "Revenu", "Non")),
                List.of(new BankImportRuleModel("r-1", "CARREFOUR", "cat-1")),
                List.of(new BankTransactionModel("tx-1", "2026-09-01", "CARREFOUR MARKET", "CB", bd("-82.35"), "cat-1",
                                List.of(new BankTransactionSplitModel("s-1", "cat-1", bd("-60.00"), "Alimentaire"),
                                        new BankTransactionSplitModel("s-2", "cat-2", bd("-22.35"), "Divers"))),
                        new BankTransactionModel("tx-2", "2026-09-02", "VIREMENT SALAIRE", bd("2500.00"))),
                List.of(new PendingOperationModel("po-1", "2026-09-03", "2026-09-05", "cheque", "0001234", "Cheque loyer",
                        bd("-900.00"), "cat-1", "pending", null, null, "note", List.of(), "line-9")),
                List.of(new MatchingModel("2026-09",
                        List.of(new MatchingLinkModel("line-1", List.of("tx-1", "tx-2"))))));
    }

    private BankImportModel saveAndReload(BankImportModel model) {
        repository.deleteAll();
        repository.flush();
        repository.save(BankImportDocumentMapper.toEntity(model));
        em.flush();
        em.clear();
        return BankImportDocumentMapper.toModel(repository.findFirstByOrderByIdAsc().orElseThrow());
    }

    private static void assertSameContent(BankImportModel actual, BankImportModel expected) {
        assertThat(actual).usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("Import complet (mapping, categories accentuees, regles, ventilations, attentes, rapprochements) relu a l'identique")
    void roundTripKeepsContent() {
        BankImportModel model = sampleImport();

        assertSameContent(saveAndReload(model), model);
    }

    @Test
    @DisplayName("Import volumineux (milliers de transactions) -> la colonne TEXT n'impose pas de limite courte")
    void largeImportIsStored() {
        List<BankTransactionModel> transactions = new ArrayList<>();
        for (int i = 0; i < 3000; i++) {
            transactions.add(new BankTransactionModel("tx-" + i, "2026-01-01", "Operation numero " + i,
                    bd("-" + i + ".50")));
        }
        BankImportModel model = new BankImportModel(transactions, List.of(), List.of());

        BankImportModel reloaded = saveAndReload(model);

        assertThat(reloaded.transactions()).hasSize(3000);
        assertSameContent(reloaded, model);
    }

    @Test
    @DisplayName("Import vide -> valeurs par defaut du modele, listes vides")
    void emptyImportKeepsModelDefaults() {
        BankImportModel reloaded = saveAndReload(new BankImportModel(null, null, null, null, null, null));

        assertThat(reloaded.columnMapping().delimiter()).isEqualTo(";");
        assertThat(reloaded.columnMapping().dateFormat()).isEqualTo("DD/MM/YYYY");
        assertThat(reloaded.transactions()).isEmpty();
        assertThat(reloaded.pendingOperations()).isEmpty();
        assertThat(reloaded.matchings()).isEmpty();
    }

    @Test
    @DisplayName("Remplacer le contenu (suppression, flush, reinsertion) ne laisse qu'une seule ligne a jour")
    void replacingContentKeepsSingleRow() {
        saveAndReload(sampleImport());

        BankImportModel replacement = new BankImportModel(
                List.of(new BankTransactionModel("tx-9", "2026-10-01", "Nouvelle operation", bd("12.00"))),
                List.of(), List.of());
        BankImportModel reloaded = saveAndReload(replacement);

        assertThat(repository.count()).isEqualTo(1L);
        assertSameContent(reloaded, replacement);
    }

    @Test
    @DisplayName("Le mapper tolere null et un contenu vide ; un JSON inconnu est ignore, un JSON invalide est refuse")
    void mapperHandlesNullBlankUnknownAndInvalidContent() {
        assertThat(BankImportDocumentMapper.toEntity(null)).isNull();
        assertThat(BankImportDocumentMapper.toModel(null)).isNull();
        assertThat(BankImportDocumentMapper.toModel(new BankImportDocumentEntity(null))).isNull();
        assertThat(BankImportDocumentMapper.toModel(new BankImportDocumentEntity("  "))).isNull();

        BankImportModel withUnknown = BankImportDocumentMapper.toModel(
                new BankImportDocumentEntity("{\"champInconnu\":1,\"transactions\":[]}"));
        assertThat(withUnknown.transactions()).isEmpty();

        assertThatThrownBy(() -> BankImportDocumentMapper.toModel(new BankImportDocumentEntity("{pas du json")))
                .isInstanceOf(IllegalStateException.class);
    }
}
