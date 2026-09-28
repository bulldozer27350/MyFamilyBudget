package com.moe.myfamilybudget.server.internal.port;

import com.moe.myfamilybudget.server.internal.model.RetirementModel;

/**
 * Port de lecture pour le domaine Retraite (RF-B00).
 */
public interface RetirementReader {

    RetirementModel getRetirement();
}