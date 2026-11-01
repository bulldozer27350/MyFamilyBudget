package com.moe.myfamilybudget.domain.retirement.port;

import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;

/**
 * Port de lecture pour le domaine Retraite (RF-B00).
 */
public interface RetirementReader {

    RetirementModel getRetirement();
}