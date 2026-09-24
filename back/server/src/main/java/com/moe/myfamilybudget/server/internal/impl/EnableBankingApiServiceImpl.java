package com.moe.myfamilybudget.server.internal.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.EnableBankingApi;
import com.moe.myfamilybudget.server.internal.enablebanking.EnableBankingException;
import com.moe.myfamilybudget.server.internal.enablebanking.EnableBankingSyncResult;
import com.moe.myfamilybudget.server.internal.enablebanking.EnableBankingSyncService;

/**
 * Implémentation du contrat OpenAPI {@code EnableBankingApi} (tag "Enable Banking") : déclenche
 * une synchronisation Enable Banking à la demande (bouton du front), en s'appuyant sur
 * {@link EnableBankingSyncService} — le même service que celui utilisé par le rafraîchissement
 * planifié ({@code EnableBankingSyncScheduler}).
 */
@Service
@RestController
public class EnableBankingApiServiceImpl implements EnableBankingApi {

    private final EnableBankingSyncService syncService;

    public EnableBankingApiServiceImpl(EnableBankingSyncService syncService) {
        this.syncService = syncService;
    }

    @Override
    public ResponseEntity<Object> syncEnableBanking() {
        if (!syncService.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", syncService.unavailableReason()));
        }

        try {
            EnableBankingSyncResult result = syncService.sync();
            return ResponseEntity.ok(toResponseMap(result));
        } catch (EnableBankingException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> toResponseMap(EnableBankingSyncResult result) {
        List<Map<String, Object>> accounts = new ArrayList<>();
        for (EnableBankingSyncResult.AccountResult account : result.accounts()) {
            Map<String, Object> accountMap = new LinkedHashMap<>();
            accountMap.put("label", account.label());
            accountMap.put("imported", account.imported());
            accountMap.put("duplicates", account.duplicates());
            accountMap.put("autoCategorized", account.autoCategorized());
            accountMap.put("error", account.error());
            accounts.add(accountMap);
        }
        return Map.of("accounts", accounts);
    }
}
