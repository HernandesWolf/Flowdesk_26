package com.flowdesk.notification;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipientIdAndCompanyId(UUID recipientId, UUID companyId, Pageable pageable);

    Page<Notification> findByRecipientIdAndCompanyIdAndReadFalse(UUID recipientId, UUID companyId, Pageable pageable);

    long countByRecipientIdAndCompanyIdAndReadFalse(UUID recipientId, UUID companyId);

    Optional<Notification> findByIdAndRecipientIdAndCompanyId(UUID id, UUID recipientId, UUID companyId);

    @Modifying
    @Query("update Notification n set n.read = true "
            + "where n.recipientId = :recipientId and n.companyId = :companyId and n.read = false")
    int markAllRead(@Param("recipientId") UUID recipientId, @Param("companyId") UUID companyId);
}
