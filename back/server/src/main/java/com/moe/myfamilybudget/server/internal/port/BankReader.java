package com.moe.myfamilybudget.server.internal.port;

import com.moe.myfamilybudget.server.internal.model.BankImportModel;

/**
 * Port de lecture pour le domaine Banque / Import (RF-B00).
 */
public interface BankReader {

    BankImportModel getBankImport();
}