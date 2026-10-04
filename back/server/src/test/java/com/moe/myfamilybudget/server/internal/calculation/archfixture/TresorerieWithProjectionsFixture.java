package com.moe.myfamilybudget.server.internal.calculation.archfixture;

import com.moe.myfamilybudget.domain.treasury.calculation.TreasuryPensionProjection;
import com.moe.myfamilybudget.domain.treasury.calculation.TreasuryPlacementCashflow;
import com.moe.myfamilybudget.domain.treasury.calculation.TreasuryTaxProjection;

/**
 * Fixture ARCH-020 conforme : Trésorerie consomme uniquement ses propres contrats d'entrée (SILO-132).
 */
public abstract class TresorerieWithProjectionsFixture {
    protected TreasuryPensionProjection retirement;
    protected TreasuryTaxProjection tax;
    protected TreasuryPlacementCashflow placement;
}
