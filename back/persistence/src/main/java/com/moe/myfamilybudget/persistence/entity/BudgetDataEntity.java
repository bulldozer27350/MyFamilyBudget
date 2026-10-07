package com.moe.myfamilybudget.persistence.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Entite racine de persistance pour les silos non encore autonomises (SILO-216).
 * Les revenus, charges, depenses ponctuelles, virements, revenus variables et surcharges
 * ont ete deplaces vers les tables autonomes cashflow_* (silo Tresorerie).
 */
@Entity
@Table(name = "budget_data")
public class BudgetDataEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private SettingsEntity settings;
    
    @OneToMany(mappedBy = "budgetData", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<PlacementEntity> placements = new ArrayList<>();
    
    @OneToMany(mappedBy = "budgetData", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<RealEstateEntity> realEstate = new ArrayList<>();
    
    @OneToMany(mappedBy = "budgetData", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
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
    
    public List<AssetCategoryEntity> getAssetCategories() {
        return assetCategories;
    }
    
    public void setAssetCategories(List<AssetCategoryEntity> assetCategories) {
        this.assetCategories = assetCategories;
    }
}
