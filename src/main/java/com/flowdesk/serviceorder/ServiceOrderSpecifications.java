package com.flowdesk.serviceorder;

import jakarta.persistence.criteria.JoinType;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.jpa.domain.Specification;

/** Composable filters for the order list. Every query starts from {@link #forCompany(UUID)}. */
public final class ServiceOrderSpecifications {

    private static final Pattern CODE = Pattern.compile("^(?:os-?)?(\\d{1,12})$", Pattern.CASE_INSENSITIVE);

    private ServiceOrderSpecifications() {}

    public static Specification<ServiceOrder> forCompany(UUID companyId) {
        return (root, query, cb) -> cb.equal(root.get("companyId"), companyId);
    }

    public static Specification<ServiceOrder> hasStatus(ServiceOrderStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<ServiceOrder> hasPriority(Priority priority) {
        return (root, query, cb) -> cb.equal(root.get("priority"), priority);
    }

    public static Specification<ServiceOrder> assignedTo(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("assignee").get("id"), userId);
    }

    public static Specification<ServiceOrder> forCustomer(UUID customerId) {
        return (root, query, cb) -> cb.equal(root.get("customer").get("id"), customerId);
    }

    public static Specification<ServiceOrder> overdue(Instant now) {
        return (root, query, cb) -> cb.and(
                cb.lessThan(root.get("dueAt"), now),
                cb.not(root.get("status").in(ServiceOrderStatus.finishedStatuses())));
    }

    public static Specification<ServiceOrder> createdBetween(Instant from, Instant to) {
        return (root, query, cb) ->
                cb.and(cb.greaterThanOrEqualTo(root.get("createdAt"), from), cb.lessThan(root.get("createdAt"), to));
    }

    /** Matches the title, the customer name, or the order number ("42" or "OS-000042"). */
    public static Specification<ServiceOrder> matchesText(String text) {
        String trimmed = text.trim();
        String pattern = "%" + trimmed.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        Matcher code = CODE.matcher(trimmed);
        return (root, query, cb) -> {
            var title = cb.like(cb.lower(root.get("title")), pattern, '\\');
            var customerName = cb.like(cb.lower(root.join("customer", JoinType.LEFT).get("name")), pattern, '\\');
            if (code.matches()) {
                return cb.or(title, customerName, cb.equal(root.get("number"), Long.parseLong(code.group(1))));
            }
            return cb.or(title, customerName);
        };
    }
}
