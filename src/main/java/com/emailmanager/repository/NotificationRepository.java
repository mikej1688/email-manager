package com.emailmanager.repository;

import com.emailmanager.entity.Email;
import com.emailmanager.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByIsReadFalseOrderBySentAtDesc();

    List<Notification> findByTypeAndIsReadFalse(Notification.NotificationType type);

    List<Notification> findByEmail(Email email);
}
