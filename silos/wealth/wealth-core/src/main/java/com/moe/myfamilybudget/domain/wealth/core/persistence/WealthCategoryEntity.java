package com.moe.myfamilybudget.domain.wealth.core.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Categorie d'actifs du domaine Patrimoine, cible JPA autonome (DB-1050). Aucune relation vers
 * {@code BudgetDataEntity} ; additive, pas encore utilisee par {@code PatrimoinePersistenceAdapter}
 * (bascule en DB-1051). Le chemin legacy ({@code asset_category}) reste inchange. L'icone est un emoji : la
 * colonne doit accepter l'UTF-8 sur 4 octets (cas par defaut de H2 et de PostgreSQL en UTF8).
 */
@Entity
@Table(name = "wealth_category")
public class WealthCategoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uid", length = 64)
    private String uid;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "icon", length = 32)
    private String icon;

    @Column(name = "name")
    private String name;

    @Column(name = "bucket", length = 64)
    private String bucket;

    @Column(name = "color", length = 32)
    private String color;

    public WealthCategoryEntity() {}

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

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
