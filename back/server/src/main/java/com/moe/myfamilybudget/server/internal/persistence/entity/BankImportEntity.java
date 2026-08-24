package com.moe.myfamilybudget.server.internal.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "bank_import")
public class BankImportEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_data_id")
    private BudgetDataEntity budgetData;
    
    // Simplified version - storing the data as JSON for complex nested structures
    @Lob
    private String jsonData;
    
    // Constructors
    public BankImportEntity() {}
    
    public BankImportEntity(String jsonData) {
        this.jsonData = jsonData;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public BudgetDataEntity getBudgetData() {
        return budgetData;
    }
    
    public void setBudgetData(BudgetDataEntity budgetData) {
        this.budgetData = budgetData;
    }
    
    public String getJsonData() {
        return jsonData;
    }
    
    public void setJsonData(String jsonData) {
        this.jsonData = jsonData;
    }
}
