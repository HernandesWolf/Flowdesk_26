package com.flowdesk.serviceorder;

import com.flowdesk.config.FlowDeskProperties;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Turns a priority into a due date using the configured SLA targets. */
@Component
public class SlaCalculator {

    private final FlowDeskProperties.Sla sla;

    public SlaCalculator(FlowDeskProperties props) {
        this.sla = props.sla();
    }

    public Instant dueAt(Priority priority, Instant from) {
        return from.plus(Duration.ofHours(hoursFor(priority)));
    }

    int hoursFor(Priority priority) {
        return switch (priority) {
            case URGENT -> sla.urgentHours();
            case HIGH -> sla.highHours();
            case MEDIUM -> sla.mediumHours();
            case LOW -> sla.lowHours();
        };
    }
}
