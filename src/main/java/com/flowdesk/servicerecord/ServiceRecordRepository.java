package com.flowdesk.servicerecord;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRecordRepository extends JpaRepository<ServiceRecord, UUID> {

    @EntityGraph(attributePaths = "author")
    List<ServiceRecord> findByServiceOrderIdAndCompanyIdOrderByCreatedAtAsc(UUID serviceOrderId, UUID companyId);
}
