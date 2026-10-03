package com.moe.myfamilybudget.persistence.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * Placement / compte du domaine Patrimoine, cible JPA autonome (DB-1050).
 *
 * <p>Contrairement a {@link PlacementEntity}, cette entite n'a <strong>aucune relation vers
 * {@link BudgetDataEntity}</strong> : elle est la racine de son propre agregat (avec l'historique de
 * valorisation). Elle est additive et n'est pas encore utilisee par {@code PatrimoinePersistenceAdapter}
 * (bascule en DB-1051). Le chemin legacy ({@code placement}, {@code placement_history_entry}) reste inchange.
 * Cle technique generee, {@code uid} = identifiant metier (aucune hypothese d'unicite, comme dans le chemin
 * legacy) et {@code position} pour restituer la liste dans l'ordre de saisie.
 *
 * <p>Les taux sont stockes avec 8 decimales (une precision par defaut les arrondirait, voir
 * {@code RatePrecisionPersistenceTest}), les montants avec 2 decimales.
 */
@Entity
@Table(name = "wealth_placement")
public class WealthPlacementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uid", length = 64)
    private String uid;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "label")
    private String label;

    @Column(name = "category")
    private String category;

    @Column(name = "balance", precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "balance_date", length = 32)
    private String balanceDate;

    @Column(name = "monthly", precision = 19, scale = 2)
    private BigDecimal monthly;

    @Column(name = "monthly_from", length = 32)
    private String monthlyFrom;

    @Column(name = "monthly_until", length = 32)
    private String monthlyUntil;

    @Column(name = "rate_pess", precision = 19, scale = 8)
    private BigDecimal ratePess;

    @Column(name = "rate_corr", precision = 19, scale = 8)
    private BigDecimal rateCorr;

    @Column(name = "rate_opti", precision = 19, scale = 8)
    private BigDecimal rateOpti;

    @Column(name = "excluded_from_retirement")
    private Boolean excludedFromRetirement;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "sweep_priority")
    private Integer sweepPriority;

    @Column(name = "sweep_cap", precision = 19, scale = 2)
    private BigDecimal sweepCap;

    @Column(name = "pause_trigger_balance", precision = 19, scale = 2)
    private BigDecimal pauseTriggerBalance;

    @Column(name = "pause_priority")
    private Integer pausePriority;

    @Column(name = "category_id", length = 64)
    private String categoryId;

    @OneToMany(mappedBy = "placement", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("position ASC")
    private List<WealthPlacementHistoryEntity> history = new ArrayList<>();

    public WealthPlacementEntity() {}

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

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public String getBalanceDate() {
        return balanceDate;
    }

    public void setBalanceDate(String balanceDate) {
        this.balanceDate = balanceDate;
    }

    public BigDecimal getMonthly() {
        return monthly;
    }

    public void setMonthly(BigDecimal monthly) {
        this.monthly = monthly;
    }

    public String getMonthlyFrom() {
        return monthlyFrom;
    }

    public void setMonthlyFrom(String monthlyFrom) {
        this.monthlyFrom = monthlyFrom;
    }

    public String getMonthlyUntil() {
        return monthlyUntil;
    }

    public void setMonthlyUntil(String monthlyUntil) {
        this.monthlyUntil = monthlyUntil;
    }

    public BigDecimal getRatePess() {
        return ratePess;
    }

    public void setRatePess(BigDecimal ratePess) {
        this.ratePess = ratePess;
    }

    public BigDecimal getRateCorr() {
        return rateCorr;
    }

    public void setRateCorr(BigDecimal rateCorr) {
        this.rateCorr = rateCorr;
    }

    public BigDecimal getRateOpti() {
        return rateOpti;
    }

    public void setRateOpti(BigDecimal rateOpti) {
        this.rateOpti = rateOpti;
    }

    public Boolean getExcludedFromRetirement() {
        return excludedFromRetirement;
    }

    public void setExcludedFromRetirement(Boolean excludedFromRetirement) {
        this.excludedFromRetirement = excludedFromRetirement;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Integer getSweepPriority() {
        return sweepPriority;
    }

    public void setSweepPriority(Integer sweepPriority) {
        this.sweepPriority = sweepPriority;
    }

    public BigDecimal getSweepCap() {
        return sweepCap;
    }

    public void setSweepCap(BigDecimal sweepCap) {
        this.sweepCap = sweepCap;
    }

    public BigDecimal getPauseTriggerBalance() {
        return pauseTriggerBalance;
    }

    public void setPauseTriggerBalance(BigDecimal pauseTriggerBalance) {
        this.pauseTriggerBalance = pauseTriggerBalance;
    }

    public Integer getPausePriority() {
        return pausePriority;
    }

    public void setPausePriority(Integer pausePriority) {
        this.pausePriority = pausePriority;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public List<WealthPlacementHistoryEntity> getHistory() {
        return history;
    }

    public void setHistory(List<WealthPlacementHistoryEntity> history) {
        this.history = history;
    }
}
