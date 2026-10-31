package com.moe.myfamilybudget.domain.credit.port;

import java.util.List;

import com.moe.myfamilybudget.domain.credit.model.LoanModel;

/**
 * Port de lecture pour le domaine Prets / Credit (RF-B00).
 */
public interface LoanReader {

    List<LoanModel> getLoans();
}