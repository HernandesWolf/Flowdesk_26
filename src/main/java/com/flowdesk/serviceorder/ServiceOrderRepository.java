package com.flowdesk.serviceorder;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceOrderRepository
        extends JpaRepository<ServiceOrder, UUID>, JpaSpecificationExecutor<ServiceOrder> {

    /** Every lookup by id also takes the tenant, so a foreign id simply "does not exist". */
    Optional<ServiceOrder> findByIdAndCompanyId(UUID id, UUID companyId);

    /** Fetches customer and assignee in the same query to avoid N+1 when listing. */
    @Override
    @EntityGraph(attributePaths = {"customer", "assignee"})
    Page<ServiceOrder> findAll(Specification<ServiceOrder> spec, Pageable pageable);

    // ---- Dashboard / reports ------------------------------------------------------------------

    interface StatusCount {
        ServiceOrderStatus getStatus();

        long getTotal();
    }

    interface PriorityCount {
        Priority getPriority();

        long getTotal();
    }

    @Query("select o.status as status, count(o) as total from ServiceOrder o "
            + "where o.companyId = :companyId group by o.status")
    List<StatusCount> countByStatus(@Param("companyId") UUID companyId);

    @Query("select o.priority as priority, count(o) as total from ServiceOrder o "
            + "where o.companyId = :companyId and o.status not in :finished group by o.priority")
    List<PriorityCount> countActiveByPriority(
            @Param("companyId") UUID companyId, @Param("finished") Collection<ServiceOrderStatus> finished);

    @Query("select count(o) from ServiceOrder o "
            + "where o.companyId = :companyId and o.status not in :finished and o.dueAt < :now")
    long countOverdue(
            @Param("companyId") UUID companyId,
            @Param("now") Instant now,
            @Param("finished") Collection<ServiceOrderStatus> finished);

    @Query("select count(o) from ServiceOrder o "
            + "where o.companyId = :companyId and o.status = :status and o.completedAt >= :from")
    long countCompletedSince(
            @Param("companyId") UUID companyId, @Param("status") ServiceOrderStatus status, @Param("from") Instant from);

    @Query("select count(o) from ServiceOrder o "
            + "where o.companyId = :companyId and o.status = :status and o.completedAt >= :from "
            + "and o.completedAt <= o.dueAt")
    long countCompletedOnTimeSince(
            @Param("companyId") UUID companyId, @Param("status") ServiceOrderStatus status, @Param("from") Instant from);

    @Query(
            value = "select cast(avg(extract(epoch from (completed_at - created_at))) as double precision) "
                    + "from service_orders "
                    + "where company_id = :companyId and status = 'COMPLETED' and completed_at >= :from",
            nativeQuery = true)
    Double averageResolutionSeconds(@Param("companyId") UUID companyId, @Param("from") Instant from);

    /**
     * Columns: technician id, technician name, completed count, average resolution seconds, on-time count.
     * Mapped by position to avoid driver/alias-case differences.
     */
    @Query(
            value = "select u.id, u.name, count(o.id), "
                    + "cast(avg(extract(epoch from (o.completed_at - o.created_at))) as double precision), "
                    + "sum(case when o.completed_at <= o.due_at then 1 else 0 end) "
                    + "from service_orders o join users u on u.id = o.assignee_id "
                    + "where o.company_id = :companyId and o.status = 'COMPLETED' "
                    + "and o.completed_at >= :from and o.completed_at < :to "
                    + "group by u.id, u.name order by count(o.id) desc, u.name asc",
            nativeQuery = true)
    List<Object[]> productivityByTechnician(
            @Param("companyId") UUID companyId, @Param("from") Instant from, @Param("to") Instant to);

    // ---- SLA monitor (intentionally cross-tenant: a system job; each order carries its own company) ----

    @Query("select o from ServiceOrder o "
            + "where o.status not in :finished and o.breachNotified = false and o.dueAt < :now")
    List<ServiceOrder> findNewlyBreached(
            @Param("now") Instant now, @Param("finished") Collection<ServiceOrderStatus> finished);

    @Query("select o from ServiceOrder o "
            + "where o.status not in :finished and o.dueSoonNotified = false and o.breachNotified = false "
            + "and o.dueAt >= :now and o.dueAt < :threshold")
    List<ServiceOrder> findNewlyDueSoon(
            @Param("now") Instant now,
            @Param("threshold") Instant threshold,
            @Param("finished") Collection<ServiceOrderStatus> finished);
}
