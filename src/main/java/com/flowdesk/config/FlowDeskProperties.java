package com.flowdesk.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flowdesk")
public record FlowDeskProperties(Jwt jwt, Storage storage, Cors cors, Sla sla) {

    public record Jwt(String secret, long expirationMinutes) {}

    public record Storage(String path) {}

    public record Cors(List<String> allowedOrigins) {}

    /** SLA targets (hours until due, by priority) and monitor settings. */
    public record Sla(
            int urgentHours,
            int highHours,
            int mediumHours,
            int lowHours,
            int dueSoonMinutes,
            long checkIntervalMs) {}
}
