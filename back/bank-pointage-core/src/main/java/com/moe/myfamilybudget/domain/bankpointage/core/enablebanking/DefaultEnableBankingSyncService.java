package com.moe.myfamilybudget.domain.bankpointage.core.enablebanking;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.moe.myfamilybudget.domain.bankpointage.calculation.BankImportCalculationService;
import com.moe.myfamilybudget.domain.bankpointage.calculation.EnableBankingSyncService;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportSummaryModel;
import com.moe.myfamilybudget.domain.bankpointage.model.EnableBankingException;
import com.moe.myfamilybudget.domain.bankpointage.model.EnableBankingSyncResult;
import com.moe.myfamilybudget.domain.bankpointage.model.EnableBankingSyncResult.AccountResult;
import com.moe.myfamilybudget.domain.bankpointage.port.BankImportChange;
import com.moe.myfamilybudget.domain.bankpointage.port.BankImportModifier;
import com.moe.myfamilybudget.domain.bankpointage.port.EnableBankingSyncStateStore;

/**
 * Récupère les transactions bancaires via Enable Banking (DSP2) et les importe, en réutilisant
 * directement {@link BankImportCalculationService#importTransactions} — le même moteur que l'import CSV
 * manuel, avec la même déduplication (date + libellé + montant). Contrairement à un script
 * externe, aucun appel HTTP n'est nécessaire ici pour "revenir" vers l'application : le mapping
 * et la persistance se font dans le même processus. La persistance passe par les ports du silo Banque
 * ({@link BankWriter}, {@link EnableBankingSyncStateStore}) ; le câblage Spring est fait par le composition root.
 */
public class DefaultEnableBankingSyncService implements EnableBankingSyncService {

    private static final Logger log = LoggerFactory.getLogger(DefaultEnableBankingSyncService.class);

    /**
     * Recouvrement de sécurité (en jours) appliqué à la date de dernière synchronisation, pour ne
     * pas rater une transaction passée de "en attente" à "comptabilisée" avec une date légèrement
     * différente. La déduplication côté import absorbe les doublons.
     */
    private static final int OVERLAP_DAYS = 3;
    private static final List<String> COL_ROLES = List.of("date", "label", "type", "amount");

    private final EnableBankingConfig config;
    private final EnableBankingClient client;
    private final EnableBankingSyncStateStore stateStore;
    private final BankImportModifier bankImportModifier;
    private final BankImportCalculationService bankImportCalculationService;

    public DefaultEnableBankingSyncService(
            EnableBankingConfig config,
            EnableBankingClient client,
            EnableBankingSyncStateStore stateStore,
            BankImportModifier bankImportModifier,
            BankImportCalculationService bankImportCalculationService) {
        this.config = config;
        this.client = client;
        this.stateStore = stateStore;
        this.bankImportModifier = bankImportModifier;
        this.bankImportCalculationService = bankImportCalculationService;
    }

    @Override
    public boolean isConfigured() {
        return config.isConfigured();
    }

    @Override
    public String unavailableReason() {
        return config.unavailableReason();
    }

    /** @throws EnableBankingException si la synchronisation n'est pas configurée */
    @Override
    public EnableBankingSyncResult sync() {
        if (!config.isConfigured()) {
            throw new EnableBankingException(config.unavailableReason());
        }

        List<AccountResult> results = new ArrayList<>();
        for (EnableBankingAccounts.Account account : config.accounts()) {
            results.add(syncAccount(account));
        }
        return new EnableBankingSyncResult(results);
    }

    private AccountResult syncAccount(EnableBankingAccounts.Account account) {
        log.info("=== Synchronisation Enable Banking : {} ===", account.label());
        String lastBookingDate = stateStore.findLastBookingDate(account.uid()).orElse(null);

        String dateFrom = null;
        if (lastBookingDate != null) {
            dateFrom = LocalDate.parse(lastBookingDate).minusDays(OVERLAP_DAYS).toString();
        }

        List<JsonNode> rawTransactions;
        try {
            // Le solde n'est pas utilisé ici (l'import ne porte que sur les transactions), mais
            // l'appel est conservé : une erreur d'authentification ou de consentement expiré se
            // manifeste dès ce premier appel, avant de tenter les transactions.
            client.fetchBalances(account.uid());
            rawTransactions = client.fetchTransactions(account.uid(), dateFrom);
        } catch (EnableBankingException e) {
            log.error("Échec de récupération pour {} : {}", account.label(), e.getMessage());
            return new AccountResult(account.label(), 0, 0, 0, e.getMessage());
        }

        List<List<String>> rows = new ArrayList<>();
        for (JsonNode tx : rawTransactions) {
            JsonNode statusNode = tx.get("status");
            if (statusNode == null || !"BOOK".equals(statusNode.asText())) {
                // Seules les opérations comptabilisées sont importées ; les opérations en attente
                // (PDNG) restent gérées par le module "opérations en attente" de MyFamilyBudget.
                continue;
            }
            EnableBankingTransactionMapper.map(tx).ifPresent(row ->
                    rows.add(List.of(row.date(), row.label(), row.type(), row.amount())));
        }

        log.info("{} transaction(s) comptabilisée(s) récupérée(s) pour {}", rows.size(), account.label());

        if (rows.isEmpty()) {
            return new AccountResult(account.label(), 0, 0, 0, null);
        }

        BankImportModel.BankColumnMappingModel mapping = new BankImportModel.BankColumnMappingModel(
                ";", "YYYY-MM-DD", false, null, null, null, null);

        // SILO-213 (lot B, etape b3) : l'import est lu, complete puis ecrit sous le verrou du silo Banque ; une
        // action simultanee de l'utilisateur (import CSV, categorie, pointage) ne perd plus les transactions
        // importees, et inversement. Seul le calcul reste sous verrou : l'appel reseau a eu lieu avant.
        BankImportSummaryModel summary = bankImportModifier.modifyBankImport(current -> {
            BankImportSummaryModel computed = bankImportCalculationService.importTransactions(
                    rows, COL_ROLES, mapping,
                    current.transactions() != null ? current.transactions() : Collections.emptyList(),
                    current.rules());

            List<BankImportModel.BankTransactionModel> allTransactions =
                    new ArrayList<>(current.transactions() != null ? current.transactions() : Collections.emptyList());
            allTransactions.addAll(computed.newTransactions());

            return BankImportChange.write(new BankImportModel(
                    computed.updatedMapping(),
                    current.categories(),
                    current.rules(),
                    allTransactions,
                    current.pendingOperations(),
                    current.matchings()), computed);
        });

        String latestBookingDate = rows.stream()
                .map(row -> row.get(0))
                .filter(date -> date != null && !date.isBlank())
                .max(String::compareTo)
                .map(latest -> (lastBookingDate != null && lastBookingDate.compareTo(latest) > 0) ? lastBookingDate : latest)
                .orElse(lastBookingDate);
        stateStore.save(account.uid(), latestBookingDate, Instant.now());

        log.info("Import terminé pour {} : {} importée(s), {} doublon(s) ignoré(s), {} catégorisée(s) automatiquement",
                account.label(), summary.imported(), summary.duplicates(), summary.autoCategorized());

        return new AccountResult(account.label(), summary.imported(), summary.duplicates(), summary.autoCategorized(), null);
    }
}
