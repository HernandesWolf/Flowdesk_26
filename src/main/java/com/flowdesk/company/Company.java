package com.flowdesk.company;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A tenant. All business data hangs off a company and is never visible to another one. */
@Entity
@Table(name = "companies")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "next_order_number", nullable = false)
    private long nextOrderNumber = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Company() {}

    public Company(String name) {
        this.name = name;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    /** Per-company sequential order number. Callers must hold a lock on this row (see repository). */
    public long takeNextOrderNumber() {
        return nextOrderNumber++;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
