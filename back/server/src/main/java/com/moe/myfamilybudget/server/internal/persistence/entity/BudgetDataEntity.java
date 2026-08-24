package com.moe.myfamilybudget.server.internal.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "budget_data")
public class BudgetDataEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private SettingsEntity settings;
    
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<IncomeEntity> incomes = new ArrayList<>();
    
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ChargeEntity> charges = new ArrayList<>();
    
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PlacementEntity> placements = new ArrayList<>();
    
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RealEstateEntity> realEstate = new ArrayList<>();
    
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private RetirementEntity retirement;
    
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<TaxChildEntity> taxChildren = new ArrayList<>();
    
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<TaxBracketEntity> taxBrackets = new ArrayList<>();
    
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<TaxRateOverrideEntity> taxRateOverrides = new ArrayList<>();
    
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<TaxActualOverrideEntity> taxActualOverrides = new ArrayList<>();
    
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<OneOffExpenseEntity> oneoff = new ArrayList<>();
    
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<TransferEntity> transfers = new ArrayList<>();
    
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<VariableIncomeEntity> variableIncomes = new ArrayList<>();
    
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<VariableOverrideEntity> variableOverrides = new ArrayList<>();
    
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private BankImportEntity bankImport;
    
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<AssetCategoryEntity> assetCategories = new ArrayList<>();
    
    // Constructors
    public BudgetDataEntity() {}
    
    public BudgetDataEntity(SettingsEntity settings) {
        this.settings = settings;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public SettingsEntity getSettings() {
        return settings;
    }
    
    public void setSettings(SettingsEntity settings) {
        this.settings = settings;
    }
    
    public List<IncomeEntity> getIncomes() {
        return incomes;
    }
    
    public void setIncomes(List<IncomeEntity> incomes) {
        this.incomes = incomes;
    }
    
    public List<ChargeEntity> getCharges() {
        return charges;
    }
    
    public void setCharges(List<ChargeEntity> charges) {
        this.charges = charges;
    }
    
    public List<PlacementEntity> getPlacements() {
        return placements;
    }
    
    public void setPlacements(List<PlacementEntity> placements) {
        this.placements = placements;
    }
    
    public List<RealEstateEntity> getRealEstate() {
        return realEstate;
    }
    
    public void setRealEstate(List<RealEstateEntity> realEstate) {
        this.realEstate = realEstate;
    }
    
    public RetirementEntity getRetirement() {
        return retirement;
    }
    
    public void setRetirement(RetirementEntity retirement) {
        this.retirement = retirement;
    }
    
    public List<TaxChildEntity> getTaxChildren() {
        return taxChildren;
    }
    
    public void setTaxChildren(List<TaxChildEntity> taxChildren) {
        this.taxChildren = taxChildren;
    }
    
    public List<TaxBracketEntity> getTaxBrackets() {
        return taxBrackets;
    }
    
    public void setTaxBrackets(List<TaxBracketEntity> taxBrackets) {
        this.taxBrackets = taxBrackets;
    }
    
    public List<TaxRateOverrideEntity> getTaxRateOverrides() {
        return taxRateOverrides;
    }
    
    public void setTaxRateOverrides(List<TaxRateOverrideEntity> taxRateOverrides) {
        this.taxRateOverrides = taxRateOverrides;
    }
    
    public List<TaxActualOverrideEntity> getTaxActualOverrides() {
        return taxActualOverrides;
    }
    
    public void setTaxActualOverrides(List<TaxActualOverrideEntity> taxActualOverrides) {
        this.taxActualOverrides = taxActualOverrides;
    }
    
    public List<OneOffExpenseEntity> getOneoff() {
        return oneoff;
    }
    
    public void setOneoff(List<OneOffExpenseEntity> oneoff) {
        this.oneoff = oneoff;
    }
    
    public List<TransferEntity> getTransfers() {
        return transfers;
    }
    
    public void setTransfers(List<TransferEntity> transfers) {
        this.transfers = transfers;
    }
    
    public List<VariableIncomeEntity> getVariableIncomes() {
        return variableIncomes;
    }
    
    public void setVariableIncomes(List<VariableIncomeEntity> variableIncomes) {
        this.variableIncomes = variableIncomes;
    }
    
    public List<VariableOverrideEntity> getVariableOverrides() {
        return variableOverrides;
    }
    
    public void setVariableOverrides(List<VariableOverrideEntity> variableOverrides) {
        this.variableOverrides = variableOverrides;
    }
    
    public BankImportEntity getBankImport() {
        return bankImport;
    }
    
    public void setBankImport(BankImportEntity bankImport) {
        this.bankImport = bankImport;
    }
    
    public List<AssetCategoryEntity> getAssetCategories() {
        return assetCategories;
    }
    
    public void setAssetCategories(List<AssetCategoryEntity> assetCategories) {
        this.assetCategories = assetCategories;
    }
}
