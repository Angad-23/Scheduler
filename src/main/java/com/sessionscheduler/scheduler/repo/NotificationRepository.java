package com.sessionscheduler.scheduler.repo;

import com.sessionscheduler.scheduler.model.AppUser;
import com.sessionscheduler.scheduler.model.Notification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    long countByRecipientAndReadFlagFalse(AppUser recipient);
    List<Notification> findTop20ByRecipientOrderByCreatedAtDesc(AppUser recipient);

    @Modifying
    @Query("update Notification n set n.readFlag = true where n.recipient = :user and n.readFlag = false")
    int markAllRead(@Param("user") AppUser user);
}
