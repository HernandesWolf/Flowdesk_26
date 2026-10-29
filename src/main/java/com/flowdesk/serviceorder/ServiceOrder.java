package com.flowdesk.serviceorder;

import com.flowdesk.customer.Customer;
import com.flowdesk.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_orders")
public class ServiceOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "order_number", nullable = false, updatable = false)
    private long number;

    @Column(nullable = false, length = 200)
    private String title;

    @Column
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false, updatable = false)
    private User createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ServiceOrderStatus status = ServiceOrderStatus.OPEN;

    @Column
    private String diagnosis;

    @Column
    private String resolution;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "due_soon_notified", nullable = false)
    private boolean dueSoonNotified;

    @Column(name = "breach_notified", nullable = false)
    private boolean breachNotified;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Optimistic lock: two people moving the same order at once cannot silently overwrite each other. */
    @Version
    private long version;

    protected ServiceOrder() {}

    public ServiceOrder(
            UUID companyId,
            long number,
            String title,
            String description,
            Customer customer,
            User createdBy,
            Priority priority,
            Instant dueAt) {
        this.companyId = companyId;
        this.number = number;
        this.title = title;
        this.description = description;
        this.customer = customer;
        this.createdBy = createdBy;
        this.priority = priority;
        this.dueAt = dueAt;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getCode() {
        return String.format("OS-%06d", number);
    }

    public boolean isOverdue(Instant now) {
        return !status.isFinished() && now.isAfter(dueAt);
    }

    public void updateDetails(String title, String description) {
        this.title = title;
        this.description = description;
    }

    /** Changing priority moves the due date and re-arms the SLA alerts. */
    public void changePriority(Priority priority, Instant newDueAt) {
        this.priority = priority;
        this.dueAt = newDueAt;
        this.dueSoonNotified = false;
        this.breachNotified = false;
    }

    public void assignTo(User technician) {
        this.assignee = technician;
    }

    public void setDiagnosis(String diagnosis) {
        this.diagnosis = diagnosis;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public void setCancellationReason(String reason) {
        this.cancellationReason = reason;
    }

    /** Applies an already-validated transition (see {@link ServiceOrderWorkflow}). */
    public void applyStatus(ServiceOrderStatus target, Instant now) {
        this.status = target;
        this.completedAt = target == ServiceOrderStatus.COMPLETED ? now : null;
    }

    public void markDueSoonNotified() {
        this.dueSoonNotified = true;
    }

    public void markBreachNotified() {
        this.breachNotified = true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public long getNumber() {
        return number;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Customer getCustomer() {
        return customer;
    }

    public User getAssignee() {
        return assignee;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Priority getPriority() {
        return priority;
    }

    public ServiceOrderStatus getStatus() {
        return status;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public String getResolution() {
        return resolution;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
