package com.moe.myfamilybudget.domain.bankpointage.port;

import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;

/**
 * Port de lecture pour le domaine Banque / Import (RF-B00).
 */
public interface BankReader {

    BankImportModel getBankImport();
}