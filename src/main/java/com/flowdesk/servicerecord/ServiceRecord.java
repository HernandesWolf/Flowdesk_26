package com.flowdesk.servicerecord;

import com.flowdesk.serviceorder.ServiceOrder;
import com.flowdesk.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A technician's note on an order: what was done, optionally with the time spent. */
@Entity
@Table(name = "service_records")
public class ServiceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_order_id", nullable = false, updatable = false)
    private ServiceOrder serviceOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private User author;

    @Column(nullable = false)
    private String content;

    @Column(name = "minutes_spent")
    private Integer minutesSpent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ServiceRecord() {}

    public ServiceRecord(UUID companyId, ServiceOrder serviceOrder, User author, String content, Integer minutesSpent) {
        this.companyId = companyId;
        this.serviceOrder = serviceOrder;
        this.author = author;
        this.content = content;
        this.minutesSpent = minutesSpent;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public User getAuthor() {
        return author;
    }

    public String getContent() {
        return content;
    }

    public Integer getMinutesSpent() {
        return minutesSpent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
