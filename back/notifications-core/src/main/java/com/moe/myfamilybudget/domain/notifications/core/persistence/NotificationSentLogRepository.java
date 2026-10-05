package com.moe.myfamilybudget.domain.notifications.core.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationSentLogRepository extends JpaRepository<NotificationSentLogEntity, String> {
}
