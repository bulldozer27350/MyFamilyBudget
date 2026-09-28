package com.moe.myfamilybudget.server.internal.port;

import java.util.List;

import com.moe.myfamilybudget.server.internal.model.LoanModel;

/**
 * Port de lecture pour le domaine Prets / Credit (RF-B00).
 */
public interface LoanReader {

    List<LoanModel> getLoans();
}