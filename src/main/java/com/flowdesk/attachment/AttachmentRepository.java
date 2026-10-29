package com.flowdesk.attachment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    @EntityGraph(attributePaths = "uploadedBy")
    List<Attachment> findByServiceOrderIdAndCompanyIdOrderByCreatedAtAsc(UUID serviceOrderId, UUID companyId);

    Optional<Attachment> findByIdAndServiceOrderIdAndCompanyId(UUID id, UUID serviceOrderId, UUID companyId);
}
