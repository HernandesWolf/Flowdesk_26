package com.flowdesk.serviceorder;

import java.util.EnumSet;
import java.util.Set;

public enum ServiceOrderStatus {
    OPEN,
    IN_DIAGNOSIS,
    WAITING_APPROVAL,
    IN_EXECUTION,
    COMPLETED,
    CANCELLED;

    /** A finished order no longer counts for SLA tracking or workload. */
    public boolean isFinished() {
        return this == COMPLETED || this == CANCELLED;
    }

    public static Set<ServiceOrderStatus> finishedStatuses() {
        return EnumSet.of(COMPLETED, CANCELLED);
    }
}
