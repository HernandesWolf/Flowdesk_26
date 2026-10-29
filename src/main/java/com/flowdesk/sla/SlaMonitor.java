package com.flowdesk.sla;

import com.flowdesk.audit.AuditAction;
import com.flowdesk.audit.AuditService;
import com.flowdesk.config.FlowDeskProperties;
import com.flowdesk.serviceorder.ServiceOrder;
import com.flowdesk.serviceorder.ServiceOrderEvents;
import com.flowdesk.serviceorder.ServiceOrderRepository;
import com.flowdesk.serviceorder.ServiceOrderStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodically looks for orders that are about to miss, or already missed, their due date and raises one
 * alert per order. The "notified" flags make the job idempotent, so it is safe to run every minute.
 *
 * <p>This is a system job, so it deliberately queries across tenants; every event it publishes still carries
 * the order's own company id.
 */
@Component
public class SlaMonitor {

    private static final Logger log = LoggerFactory.getLogger(SlaMonitor.class);

    private final ServiceOrderRepository orders;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final FlowDeskProperties.Sla slaProps;

    public SlaMonitor(
            ServiceOrderRepository orders,
            AuditService audit,
            ApplicationEventPublisher events,
            FlowDeskProperties props) {
        this.orders = orders;
        this.audit = audit;
        this.events = events;
        this.slaProps = props.sla();
    }

    @Scheduled(
            fixedDelayString = "${flowdesk.sla.check-interval-ms}",
            initialDelayString = "${flowdesk.sla.check-interval-ms}")
    @Transactional
    public void checkSlas() {
        Instant now = Instant.now();
        var finished = ServiceOrderStatus.finishedStatuses();

        List<ServiceOrder> breached = orders.findNewlyBreached(now, finished);
        for (ServiceOrder order : breached) {
            order.markBreachNotified();
            audit.record(order, AuditAction.SLA_BREACHED, "dueAt", null, order.getDueAt().toString());
            events.publishEvent(alert(order, ServiceOrderEvents.SlaAlertType.BREACHED));
        }

        Instant threshold = now.plus(Duration.ofMinutes(slaProps.dueSoonMinutes()));
        List<ServiceOrder> dueSoon = orders.findNewlyDueSoon(now, threshold, finished);
        for (ServiceOrder order : dueSoon) {
            order.markDueSoonNotified();
            audit.record(order, AuditAction.SLA_DUE_SOON, "dueAt", null, order.getDueAt().toString());
            events.publishEvent(alert(order, ServiceOrderEvents.SlaAlertType.DUE_SOON));
        }

        if (!breached.isEmpty() || !dueSoon.isEmpty()) {
            log.info("SLA check: {} breached, {} due soon", breached.size(), dueSoon.size());
        }
    }

    private static ServiceOrderEvents.SlaAlert alert(ServiceOrder o, ServiceOrderEvents.SlaAlertType type) {
        return new ServiceOrderEvents.SlaAlert(
                o.getCompanyId(), o.getId(), o.getCode(), o.getTitle(),
                o.getAssignee() == null ? null : o.getAssignee().getId(), type);
    }
}
