package com.flowdesk.dashboard;

import java.util.Map;
import java.util.UUID;

public final class DashboardDtos {

    private DashboardDtos() {}

    /** Headline indicators for the manager's dashboard. */
    public record Summary(
            int periodDays,
            long totalOrders,
            long activeOrders,
            long overdueOrders,
            long completedInPeriod,
            Double slaCompliancePercent,
            Double averageResolutionHours,
            Map<String, Long> byStatus,
            Map<String, Long> activeByPriority) {}

    public record TechnicianProductivity(
            UUID technicianId,
            String technicianName,
            long completed,
            long completedOnTime,
            Double onTimePercent,
            Double averageResolutionHours) {}
}
