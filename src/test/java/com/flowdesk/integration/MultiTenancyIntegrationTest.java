package com.flowdesk.integration;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowdesk.support.AbstractIntegrationTest;
import com.flowdesk.user.Role;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The single most important property of a multi-tenant system: one company never reaches another's data. */
class MultiTenancyIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("Company B cannot read, list, change or attach to Company A's data")
    void companiesAreIsolated() throws Exception {
        Session a = registerCompany("Company A");
        Session b = registerCompany("Company B");
        Session technicianA = createUser(a, Role.TECHNICIAN);
        UUID customerA = createCustomer(a);
        UUID orderA = createOrder(a, customerA, technicianA.userId(), "HIGH");

        // Sanity check: the owner can see it.
        getAs(a, "/api/service-orders/" + orderA).andExpect(status().isOk());

        // Every route to A's order answers 404 for B, so even its existence is not revealed.
        String base = "/api/service-orders/" + orderA;
        getAs(b, base).andExpect(status().isNotFound());
        getAs(b, base + "/timeline").andExpect(status().isNotFound());
        getAs(b, base + "/records").andExpect(status().isNotFound());
        getAs(b, base + "/attachments").andExpect(status().isNotFound());
        getAs(b, base + "/allowed-transitions").andExpect(status().isNotFound());
        transition(b, orderA, Map.of("targetStatus", "CANCELLED", "reason", "hostile takeover"))
                .andExpect(status().isNotFound());
        putAs(b, base + "/assignee", Map.of("technicianId", technicianA.userId().toString()))
                .andExpect(status().isNotFound());

        // Lists only contain the caller's own tenant.
        getAs(b, "/api/service-orders").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        getAs(b, "/api/users").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        getAs(b, "/api/customers").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));

        // Foreign ids are rejected when referenced from B's own requests.
        getAs(b, "/api/customers/" + customerA).andExpect(status().isNotFound());
        postAs(b, "/api/service-orders", Map.of("title", "x", "customerId", customerA.toString(), "priority", "LOW"))
                .andExpect(status().isNotFound());

        UUID customerB = createCustomer(b);
        postAs(b, "/api/service-orders", Map.of(
                        "title", "x",
                        "customerId", customerB.toString(),
                        "priority", "LOW",
                        "assigneeId", technicianA.userId().toString()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Each company numbers its orders independently, starting at OS-000001")
    void orderNumbersArePerCompany() throws Exception {
        Session a = registerCompany("Numbering A");
        Session b = registerCompany("Numbering B");

        var firstA = createOrderJson(a, createCustomer(a), null, "LOW");
        var secondA = createOrderJson(a, createCustomer(a), null, "LOW");
        var firstB = createOrderJson(b, createCustomer(b), null, "LOW");

        org.assertj.core.api.Assertions.assertThat(firstA.get("code").asText()).isEqualTo("OS-000001");
        org.assertj.core.api.Assertions.assertThat(secondA.get("code").asText()).isEqualTo("OS-000002");
        org.assertj.core.api.Assertions.assertThat(firstB.get("code").asText()).isEqualTo("OS-000001");
    }
}
