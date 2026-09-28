package com.moe.myfamilybudget.server.internal.port;

import java.util.List;

import com.moe.myfamilybudget.server.internal.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.server.internal.model.TaxBracketModel;
import com.moe.myfamilybudget.server.internal.model.TaxChildModel;
import com.moe.myfamilybudget.server.internal.model.TaxRateOverrideModel;

/**
 * Port de lecture pour le domaine Fiscalite (RF-B00).
 */
public interface TaxReader {

    List<TaxChildModel> getTaxChildren();

    List<TaxBracketModel> getTaxBrackets();

    List<TaxRateOverrideModel> getTaxRateOverrides();

    List<TaxActualOverrideModel> getTaxActualOverrides();
}