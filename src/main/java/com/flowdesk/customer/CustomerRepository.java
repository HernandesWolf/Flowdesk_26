package com.flowdesk.customer;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByIdAndCompanyId(UUID id, UUID companyId);

    Page<Customer> findByCompanyId(UUID companyId, Pageable pageable);

    Page<Customer> findByCompanyIdAndNameContainingIgnoreCase(UUID companyId, String name, Pageable pageable);
}
