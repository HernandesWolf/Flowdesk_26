package com.flowdesk.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByIdAndActiveTrue(UUID id);

    Optional<User> findByIdAndCompanyId(UUID id, UUID companyId);

    List<User> findByCompanyIdOrderByNameAsc(UUID companyId);

    List<User> findByCompanyIdAndRoleAndActiveTrueOrderByNameAsc(UUID companyId, Role role);

    List<User> findByCompanyIdAndRoleInAndActiveTrue(UUID companyId, Collection<Role> roles);

    List<User> findByCompanyIdAndIdIn(UUID companyId, Collection<UUID> ids);
}
