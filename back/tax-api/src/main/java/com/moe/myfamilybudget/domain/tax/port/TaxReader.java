package com.moe.myfamilybudget.domain.tax.port;

import java.util.List;

import com.moe.myfamilybudget.domain.tax.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.domain.tax.model.TaxBracketModel;
import com.moe.myfamilybudget.domain.tax.model.TaxChildModel;
import com.moe.myfamilybudget.domain.tax.model.TaxRateOverrideModel;

/**
 * Port de lecture pour le domaine Fiscalite (RF-B00).
 */
public interface TaxReader {

    List<TaxChildModel> getTaxChildren();

    List<TaxBracketModel> getTaxBrackets();

    List<TaxRateOverrideModel> getTaxRateOverrides();

    List<TaxActualOverrideModel> getTaxActualOverrides();
}