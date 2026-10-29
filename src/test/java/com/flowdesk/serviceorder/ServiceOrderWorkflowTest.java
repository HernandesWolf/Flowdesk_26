package com.flowdesk.serviceorder;

import static com.flowdesk.serviceorder.ServiceOrderStatus.CANCELLED;
import static com.flowdesk.serviceorder.ServiceOrderStatus.COMPLETED;
import static com.flowdesk.serviceorder.ServiceOrderStatus.IN_DIAGNOSIS;
import static com.flowdesk.serviceorder.ServiceOrderStatus.IN_EXECUTION;
import static com.flowdesk.serviceorder.ServiceOrderStatus.OPEN;
import static com.flowdesk.serviceorder.ServiceOrderStatus.WAITING_APPROVAL;
import static org.assertj.core.api.Assertions.assertThat;

import com.flowdesk.serviceorder.ServiceOrderWorkflow.Outcome;
import com.flowdesk.serviceorder.ServiceOrderWorkflow.TransitionCheck;
import com.flowdesk.serviceorder.ServiceOrderWorkflow.TransitionContext;
import com.flowdesk.user.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ServiceOrderWorkflowTest {

    private static TransitionCheck check(
            ServiceOrderStatus from,
            ServiceOrderStatus to,
            Role role,
            boolean isAssignee,
            boolean hasAssignee,
            String diagnosis,
            String resolution,
            String reason) {
        return ServiceOrderWorkflow.validate(
                new TransitionContext(from, to, role, isAssignee, hasAssignee, diagnosis, resolution, reason));
    }

    @DisplayName("Structural transition matrix")
    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @CsvSource({
        "OPEN, IN_DIAGNOSIS, true",
        "OPEN, COMPLETED, false",
        "OPEN, IN_EXECUTION, false",
        "OPEN, WAITING_APPROVAL, false",
        "OPEN, CANCELLED, true",
        "IN_DIAGNOSIS, WAITING_APPROVAL, true",
        "IN_DIAGNOSIS, IN_EXECUTION, true",
        "IN_DIAGNOSIS, COMPLETED, false",
        "IN_DIAGNOSIS, OPEN, false",
        "WAITING_APPROVAL, IN_EXECUTION, true",
        "WAITING_APPROVAL, IN_DIAGNOSIS, true",
        "WAITING_APPROVAL, COMPLETED, false",
        "IN_EXECUTION, COMPLETED, true",
        "IN_EXECUTION, IN_DIAGNOSIS, true",
        "IN_EXECUTION, OPEN, false",
        "COMPLETED, IN_EXECUTION, true",
        "COMPLETED, CANCELLED, false",
        "COMPLETED, OPEN, false",
        "CANCELLED, OPEN, false",
        "CANCELLED, IN_EXECUTION, false"
    })
    void transitionMatrix(ServiceOrderStatus from, ServiceOrderStatus to, boolean allowed) {
        assertThat(ServiceOrderWorkflow.isAllowed(from, to)).isEqualTo(allowed);
    }

    @Test
    void cancelledIsTerminal() {
        assertThat(ServiceOrderWorkflow.targetsFrom(CANCELLED)).isEmpty();
    }

    @Test
    void openCannotJumpStraightToCompleted() {
        TransitionCheck c = check(OPEN, COMPLETED, Role.MANAGER, false, true, "d", "r", null);
        assertThat(c.outcome()).isEqualTo(Outcome.NOT_ALLOWED);
        assertThat(c.message()).contains("OPEN").contains("COMPLETED");
    }

    @Test
    void diagnosisRequiresAssignedTechnician() {
        TransitionCheck c = check(OPEN, IN_DIAGNOSIS, Role.MANAGER, false, false, null, null, null);
        assertThat(c.outcome()).isEqualTo(Outcome.RULE_VIOLATION);

        TransitionCheck ok = check(OPEN, IN_DIAGNOSIS, Role.MANAGER, false, true, null, null, null);
        assertThat(ok.isOk()).isTrue();
    }

    @Test
    void executionAndApprovalRequireDiagnosis() {
        assertThat(check(IN_DIAGNOSIS, IN_EXECUTION, Role.MANAGER, false, true, " ", null, null).outcome())
                .isEqualTo(Outcome.RULE_VIOLATION);
        assertThat(check(IN_DIAGNOSIS, WAITING_APPROVAL, Role.MANAGER, false, true, null, null, null).outcome())
                .isEqualTo(Outcome.RULE_VIOLATION);
        assertThat(check(IN_DIAGNOSIS, IN_EXECUTION, Role.MANAGER, false, true, "Faulty PSU", null, null).isOk())
                .isTrue();
    }

    @Test
    void completionRequiresResolution() {
        assertThat(check(IN_EXECUTION, COMPLETED, Role.TECHNICIAN, true, true, "d", "", null).outcome())
                .isEqualTo(Outcome.RULE_VIOLATION);
        assertThat(check(IN_EXECUTION, COMPLETED, Role.TECHNICIAN, true, true, "d", "Replaced PSU", null).isOk())
                .isTrue();
    }

    @Test
    void cancellationRequiresReasonAndManagerRole() {
        assertThat(check(OPEN, CANCELLED, Role.MANAGER, false, false, null, null, null).outcome())
                .isEqualTo(Outcome.RULE_VIOLATION);
        assertThat(check(OPEN, CANCELLED, Role.MANAGER, false, false, null, null, "Customer gave up").isOk())
                .isTrue();
        assertThat(check(OPEN, CANCELLED, Role.TECHNICIAN, true, true, null, null, "x").outcome())
                .isEqualTo(Outcome.FORBIDDEN);
    }

    @Test
    void technicianCanOnlyActOnOwnOrders() {
        TransitionCheck c = check(OPEN, IN_DIAGNOSIS, Role.TECHNICIAN, false, true, null, null, null);
        assertThat(c.outcome()).isEqualTo(Outcome.FORBIDDEN);
    }

    @Test
    void onlyManagersApproveAndReopen() {
        assertThat(check(WAITING_APPROVAL, IN_EXECUTION, Role.TECHNICIAN, true, true, "d", null, null).outcome())
                .isEqualTo(Outcome.FORBIDDEN);
        assertThat(check(WAITING_APPROVAL, IN_EXECUTION, Role.MANAGER, false, true, "d", null, null).isOk())
                .isTrue();
        assertThat(check(COMPLETED, IN_EXECUTION, Role.TECHNICIAN, true, true, "d", "r", null).outcome())
                .isEqualTo(Outcome.FORBIDDEN);
        assertThat(check(COMPLETED, IN_EXECUTION, Role.ADMIN, false, true, "d", "r", null).isOk()).isTrue();
    }

    @Test
    void availableTargetsDependOnRole() {
        assertThat(ServiceOrderWorkflow.availableFor(OPEN, Role.MANAGER, false))
                .containsExactlyInAnyOrder(IN_DIAGNOSIS, CANCELLED);
        assertThat(ServiceOrderWorkflow.availableFor(OPEN, Role.TECHNICIAN, true)).containsExactly(IN_DIAGNOSIS);
        assertThat(ServiceOrderWorkflow.availableFor(OPEN, Role.TECHNICIAN, false)).isEmpty();
        assertThat(ServiceOrderWorkflow.availableFor(COMPLETED, Role.TECHNICIAN, true)).isEmpty();
    }
}
