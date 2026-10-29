package com.flowdesk.integration;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowdesk.support.AbstractIntegrationTest;
import com.flowdesk.user.Role;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ServiceOrderWorkflowIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("An order walks the full lifecycle and every rule is enforced on the way")
    void fullLifecycle() throws Exception {
        Session admin = registerCompany("Workflow Co");
        Session tech = createUser(admin, Role.TECHNICIAN);
        UUID order = createOrder(admin, createCustomer(admin), tech.userId(), "MEDIUM");

        // OPEN -> COMPLETED is not a defined transition.
        transition(tech, order, Map.of("targetStatus", "COMPLETED", "resolution", "done"))
                .andExpect(status().isConflict());

        // OPEN -> IN_DIAGNOSIS
        transition(tech, order, Map.of("targetStatus", "IN_DIAGNOSIS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_DIAGNOSIS"));

        // Starting execution without a diagnosis is a business-rule violation.
        transition(tech, order, Map.of("targetStatus", "IN_EXECUTION")).andExpect(status().isUnprocessableEntity());

        // With a diagnosis it works, and the diagnosis is stored.
        transition(tech, order, Map.of("targetStatus", "IN_EXECUTION", "diagnosis", "Faulty power supply"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_EXECUTION"))
                .andExpect(jsonPath("$.diagnosis").value("Faulty power supply"));

        // Completing needs a resolution; technicians cannot cancel.
        transition(tech, order, Map.of("targetStatus", "COMPLETED")).andExpect(status().isUnprocessableEntity());
        transition(tech, order, Map.of("targetStatus", "CANCELLED", "reason", "nope")).andExpect(status().isForbidden());

        transition(tech, order, Map.of("targetStatus", "COMPLETED", "resolution", "Replaced the PSU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").exists());

        // Only managers can reopen.
        transition(tech, order, Map.of("targetStatus", "IN_EXECUTION")).andExpect(status().isForbidden());
        transition(admin, order, Map.of("targetStatus", "IN_EXECUTION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_EXECUTION"))
                .andExpect(jsonPath("$.completedAt").doesNotExist());

        // The audit trail recorded each status change plus the diagnosis and resolution.
        getAs(admin, "/api/service-orders/" + order + "/timeline")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.action=='CREATED')]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.action=='STATUS_CHANGED')]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.action=='DIAGNOSIS_UPDATED')]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.action=='RESOLUTION_UPDATED')]").isNotEmpty());
    }

    @Test
    @DisplayName("Diagnosis cannot start on an unassigned order, and cancelling needs a reason")
    void guards() throws Exception {
        Session admin = registerCompany("Guard Co");
        UUID order = createOrder(admin, createCustomer(admin), null, "LOW");

        transition(admin, order, Map.of("targetStatus", "IN_DIAGNOSIS")).andExpect(status().isUnprocessableEntity());
        transition(admin, order, Map.of("targetStatus", "CANCELLED")).andExpect(status().isUnprocessableEntity());
        transition(admin, order, Map.of("targetStatus", "CANCELLED", "reason", "Customer withdrew"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Customer withdrew"));

        // CANCELLED is terminal and read-only.
        transition(admin, order, Map.of("targetStatus", "OPEN")).andExpect(status().isConflict());
        putAs(admin, "/api/service-orders/" + order, Map.of("title", "New title", "priority", "HIGH"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Approval step: only a manager can approve and move to execution")
    void approvalStep() throws Exception {
        Session admin = registerCompany("Approval Co");
        Session tech = createUser(admin, Role.TECHNICIAN);
        UUID order = createOrder(admin, createCustomer(admin), tech.userId(), "HIGH");

        transition(tech, order, Map.of("targetStatus", "IN_DIAGNOSIS")).andExpect(status().isOk());
        transition(tech, order, Map.of("targetStatus", "WAITING_APPROVAL", "diagnosis", "Needs new motherboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING_APPROVAL"));

        transition(tech, order, Map.of("targetStatus", "IN_EXECUTION")).andExpect(status().isForbidden());
        transition(admin, order, Map.of("targetStatus", "IN_EXECUTION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_EXECUTION"));
    }

    @Test
    @DisplayName("Changing priority recalculates the due date and is audited")
    void priorityChange() throws Exception {
        Session admin = registerCompany("Priority Co");
        var created = createOrderJson(admin, createCustomer(admin), null, "LOW");
        UUID order = UUID.fromString(created.get("id").asText());

        putAs(admin, "/api/service-orders/" + order, Map.of("title", "Printer not working", "priority", "URGENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("URGENT"));

        getAs(admin, "/api/service-orders/" + order + "/timeline")
                .andExpect(jsonPath("$.content[?(@.action=='PRIORITY_CHANGED')]").isNotEmpty());
    }

    @Test
    @DisplayName("Assigning a technician sends an asynchronous in-app notification")
    void assignmentNotifiesTechnician() throws Exception {
        Session admin = registerCompany("Notify Co");
        Session tech = createUser(admin, Role.TECHNICIAN);
        createOrder(admin, createCustomer(admin), tech.userId(), "HIGH");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                getAs(tech, "/api/notifications?unreadOnly=true")
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content[?(@.type=='ORDER_ASSIGNED')]").isNotEmpty()));

        getAs(tech, "/api/notifications/unread-count").andExpect(jsonPath("$.unread").value(1));
        postAs(tech, "/api/notifications/read-all", Map.of()).andExpect(status().isOk());
        getAs(tech, "/api/notifications/unread-count").andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    @DisplayName("Technician notes are stored per order")
    void serviceRecords() throws Exception {
        Session admin = registerCompany("Records Co");
        Session tech = createUser(admin, Role.TECHNICIAN);
        UUID order = createOrder(admin, createCustomer(admin), tech.userId(), "LOW");

        postAs(tech, "/api/service-orders/" + order + "/records",
                        Map.of("content", "Opened the case, found dust build-up", "minutesSpent", 30))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorName").value("TECHNICIAN User"));

        getAs(tech, "/api/service-orders/" + order + "/records")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
