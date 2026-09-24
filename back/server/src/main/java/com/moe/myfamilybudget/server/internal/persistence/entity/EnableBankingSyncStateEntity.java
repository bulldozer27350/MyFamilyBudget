package com.moe.myfamilybudget.server.internal.persistence.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * État de synchronisation Enable Banking pour un compte (une ligne par {@code account_uid}) :
 * date de la dernière transaction importée, pour ne récupérer que les nouveautés lors des
 * synchronisations suivantes.
 */
@Entity
@Table(name = "enable_banking_sync_state")
public class EnableBankingSyncStateEntity {

    @Id
    @Column(name = "account_uid", length = 64)
    private String accountUid;

    @Column(name = "last_booking_date", length = 10)
    private String lastBookingDate;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    public EnableBankingSyncStateEntity() {
    }

    public EnableBankingSyncStateEntity(String accountUid, String lastBookingDate, Instant lastSyncAt) {
        this.accountUid = accountUid;
        this.lastBookingDate = lastBookingDate;
        this.lastSyncAt = lastSyncAt;
    }

    public String getAccountUid() {
        return accountUid;
    }

    public void setAccountUid(String accountUid) {
        this.accountUid = accountUid;
    }

    public String getLastBookingDate() {
        return lastBookingDate;
    }

    public void setLastBookingDate(String lastBookingDate) {
        this.lastBookingDate = lastBookingDate;
    }

    public Instant getLastSyncAt() {
        return lastSyncAt;
    }

    public void setLastSyncAt(Instant lastSyncAt) {
        this.lastSyncAt = lastSyncAt;
    }
}
