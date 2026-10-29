package com.flowdesk.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowdesk.support.AbstractIntegrationTest;
import com.flowdesk.user.Role;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class DashboardIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("Dashboard indicators and productivity report reflect completed work")
    void indicatorsAndProductivity() throws Exception {
        Session admin = registerCompany("Dashboard Co");
        Session tech = createUser(admin, Role.TECHNICIAN);
        UUID customer = createCustomer(admin);

        UUID done = createOrder(admin, customer, tech.userId(), "HIGH");
        createOrder(admin, customer, tech.userId(), "LOW"); // stays OPEN

        transition(tech, done, Map.of("targetStatus", "IN_DIAGNOSIS")).andExpect(status().isOk());
        transition(tech, done, Map.of("targetStatus", "IN_EXECUTION", "diagnosis", "d")).andExpect(status().isOk());
        transition(tech, done, Map.of("targetStatus", "COMPLETED", "resolution", "fixed")).andExpect(status().isOk());

        getAs(admin, "/api/dashboard/summary?days=7")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(2))
                .andExpect(jsonPath("$.activeOrders").value(1))
                .andExpect(jsonPath("$.overdueOrders").value(0))
                .andExpect(jsonPath("$.completedInPeriod").value(1))
                .andExpect(jsonPath("$.slaCompliancePercent").value(100.0))
                .andExpect(jsonPath("$.byStatus.COMPLETED").value(1))
                .andExpect(jsonPath("$.byStatus.OPEN").value(1))
                .andExpect(jsonPath("$.activeByPriority.LOW").value(1));

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String range = "from=" + today.minusDays(1) + "&to=" + today.plusDays(1);

        getAs(admin, "/api/reports/productivity?" + range)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].technicianName").value("TECHNICIAN User"))
                .andExpect(jsonPath("$[0].completed").value(1))
                .andExpect(jsonPath("$[0].onTimePercent").value(100.0));

        MvcResult csv = getAs(admin, "/api/reports/service-orders.csv?" + range)
                .andExpect(status().isOk())
                .andReturn();
        String body = csv.getResponse().getContentAsString();
        assertThat(csv.getResponse().getContentType()).startsWith("text/csv");
        assertThat(body).startsWith("code,title,customer,assignee,priority,status");
        assertThat(body.lines().count()).isEqualTo(3); // header + 2 orders
    }

    @Test
    @DisplayName("Invalid report ranges are rejected")
    void invalidRange() throws Exception {
        Session admin = registerCompany("Range Co");
        getAs(admin, "/api/reports/productivity?from=2026-02-01&to=2026-01-01").andExpect(status().isBadRequest());
        getAs(admin, "/api/reports/productivity?from=2020-01-01&to=2026-01-01").andExpect(status().isBadRequest());
        getAs(admin, "/api/reports/productivity?from=garbage&to=2026-01-01").andExpect(status().isBadRequest());
    }
}
