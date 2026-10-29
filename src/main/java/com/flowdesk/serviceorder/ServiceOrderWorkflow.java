package com.flowdesk.serviceorder;

import static com.flowdesk.serviceorder.ServiceOrderStatus.CANCELLED;
import static com.flowdesk.serviceorder.ServiceOrderStatus.COMPLETED;
import static com.flowdesk.serviceorder.ServiceOrderStatus.IN_DIAGNOSIS;
import static com.flowdesk.serviceorder.ServiceOrderStatus.IN_EXECUTION;
import static com.flowdesk.serviceorder.ServiceOrderStatus.OPEN;
import static com.flowdesk.serviceorder.ServiceOrderStatus.WAITING_APPROVAL;

import com.flowdesk.user.Role;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Explicit finite-state machine for service orders. Framework-free on purpose: every business rule
 * about "who can move an order where, and what must be filled in first" lives here and is unit tested
 * without Spring or a database.
 *
 * <pre>
 * OPEN -> IN_DIAGNOSIS -> WAITING_APPROVAL -> IN_EXECUTION -> COMPLETED
 *                    \---------------------/        ^             |
 *                                                   \--- reopen --/
 * any non-final state -> CANCELLED
 * </pre>
 */
public final class ServiceOrderWorkflow {

    private static final Map<ServiceOrderStatus, Set<ServiceOrderStatus>> TRANSITIONS =
            new EnumMap<>(ServiceOrderStatus.class);

    static {
        TRANSITIONS.put(OPEN, EnumSet.of(IN_DIAGNOSIS, CANCELLED));
        TRANSITIONS.put(IN_DIAGNOSIS, EnumSet.of(WAITING_APPROVAL, IN_EXECUTION, CANCELLED));
        TRANSITIONS.put(WAITING_APPROVAL, EnumSet.of(IN_EXECUTION, IN_DIAGNOSIS, CANCELLED));
        TRANSITIONS.put(IN_EXECUTION, EnumSet.of(COMPLETED, IN_DIAGNOSIS, CANCELLED));
        TRANSITIONS.put(COMPLETED, EnumSet.of(IN_EXECUTION));
        TRANSITIONS.put(CANCELLED, EnumSet.noneOf(ServiceOrderStatus.class));
    }

    private ServiceOrderWorkflow() {}

    /** Everything the state machine structurally allows from {@code from}, ignoring who asks. */
    public static Set<ServiceOrderStatus> targetsFrom(ServiceOrderStatus from) {
        return Collections.unmodifiableSet(TRANSITIONS.get(from));
    }

    public static boolean isAllowed(ServiceOrderStatus from, ServiceOrderStatus to) {
        return TRANSITIONS.get(from).contains(to);
    }

    /** Targets this actor may request from {@code from} (role rules only, data rules are checked on submit). */
    public static List<ServiceOrderStatus> availableFor(
            ServiceOrderStatus from, Role role, boolean actorIsAssignee) {
        List<ServiceOrderStatus> result = new ArrayList<>();
        for (ServiceOrderStatus to : TRANSITIONS.get(from)) {
            if (roleViolation(from, to, role, actorIsAssignee).isEmpty()) {
                result.add(to);
            }
        }
        return result;
    }

    public static TransitionCheck validate(TransitionContext c) {
        if (!isAllowed(c.from(), c.to())) {
            return TransitionCheck.notAllowed(
                    "Cannot move a service order from " + c.from() + " to " + c.to()
                            + ". Allowed targets: " + TRANSITIONS.get(c.from()));
        }

        Optional<String> forbidden = roleViolation(c.from(), c.to(), c.actorRole(), c.actorIsAssignee());
        if (forbidden.isPresent()) {
            return TransitionCheck.forbidden(forbidden.get());
        }

        List<String> violations = new ArrayList<>();
        if (c.to() == IN_DIAGNOSIS && !c.hasAssignee()) {
            violations.add("A technician must be assigned before the diagnosis can start");
        }
        boolean needsDiagnosis =
                c.to() == WAITING_APPROVAL || (c.to() == IN_EXECUTION && c.from() == IN_DIAGNOSIS);
        if (needsDiagnosis && isBlank(c.diagnosis())) {
            violations.add("A diagnosis is required before requesting approval or starting the execution");
        }
        if (c.to() == COMPLETED && isBlank(c.resolution())) {
            violations.add("A resolution summary is required to complete a service order");
        }
        if (c.to() == CANCELLED && isBlank(c.reason())) {
            violations.add("A cancellation reason is required");
        }
        return violations.isEmpty() ? TransitionCheck.ok() : TransitionCheck.ruleViolation(violations);
    }

    private static Optional<String> roleViolation(
            ServiceOrderStatus from, ServiceOrderStatus to, Role role, boolean actorIsAssignee) {
        if (role.isManagerOrAdmin()) {
            return Optional.empty();
        }
        if (!actorIsAssignee) {
            return Optional.of("Only the assigned technician can change the status of this service order");
        }
        if (to == CANCELLED) {
            return Optional.of("Only managers can cancel a service order");
        }
        if (from == COMPLETED) {
            return Optional.of("Only managers can reopen a completed service order");
        }
        if (from == WAITING_APPROVAL && to == IN_EXECUTION) {
            return Optional.of("Only managers can register the customer's approval");
        }
        return Optional.empty();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Facts the workflow needs to decide; deliberately free of entities. */
    public record TransitionContext(
            ServiceOrderStatus from,
            ServiceOrderStatus to,
            Role actorRole,
            boolean actorIsAssignee,
            boolean hasAssignee,
            String diagnosis,
            String resolution,
            String reason) {}

    public enum Outcome {
        OK,
        NOT_ALLOWED,
        FORBIDDEN,
        RULE_VIOLATION
    }

    public record TransitionCheck(Outcome outcome, List<String> messages) {

        static TransitionCheck ok() {
            return new TransitionCheck(Outcome.OK, List.of());
        }

        static TransitionCheck notAllowed(String message) {
            return new TransitionCheck(Outcome.NOT_ALLOWED, List.of(message));
        }

        static TransitionCheck forbidden(String message) {
            return new TransitionCheck(Outcome.FORBIDDEN, List.of(message));
        }

        static TransitionCheck ruleViolation(List<String> messages) {
            return new TransitionCheck(Outcome.RULE_VIOLATION, List.copyOf(messages));
        }

        public boolean isOk() {
            return outcome == Outcome.OK;
        }

        public String message() {
            return String.join("; ", messages);
        }
    }
}
