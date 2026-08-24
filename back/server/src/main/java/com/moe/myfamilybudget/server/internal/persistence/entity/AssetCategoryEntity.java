package com.moe.myfamilybudget.server.internal.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "asset_category")
public class AssetCategoryEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String uid;
    private String icon;
    private String name;
    private String bucket;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_data_id")
    private BudgetDataEntity budgetData;
    
    // Constructors
    public AssetCategoryEntity() {}
    
    public AssetCategoryEntity(String uid, String icon, String name, String bucket) {
        this.uid = uid;
        this.icon = icon;
        this.name = name;
        this.bucket = bucket;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getUid() {
        return uid;
    }
    
    public void setUid(String uid) {
        this.uid = uid;
    }
    
    public String getIcon() {
        return icon;
    }
    
    public void setIcon(String icon) {
        this.icon = icon;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getBucket() {
        return bucket;
    }
    
    public void setBucket(String bucket) {
        this.bucket = bucket;
    }
    
    public BudgetDataEntity getBudgetData() {
        return budgetData;
    }
    
    public void setBudgetData(BudgetDataEntity budgetData) {
        this.budgetData = budgetData;
    }
}
