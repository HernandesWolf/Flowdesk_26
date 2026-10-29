package com.flowdesk.integration;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowdesk.serviceorder.Priority;
import com.flowdesk.serviceorder.ServiceOrder;
import com.flowdesk.serviceorder.ServiceOrderRepository;
import com.flowdesk.sla.SlaMonitor;
import com.flowdesk.support.AbstractIntegrationTest;
import com.flowdesk.user.Role;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class SlaMonitorIntegrationTest extends AbstractIntegrationTest {

    @Autowired SlaMonitor slaMonitor;
    @Autowired ServiceOrderRepository orderRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("An overdue order raises exactly one SLA breach alert, for the technician and the managers")
    void breachAlertIsRaisedOnce() throws Exception {
        Session admin = registerCompany("SLA Co");
        Session tech = createUser(admin, Role.TECHNICIAN);
        UUID order = createOrder(admin, createCustomer(admin), tech.userId(), "LOW");

        // Pretend the order's due date passed a minute ago.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ServiceOrder o = orderRepository.findById(order).orElseThrow();
            o.changePriority(Priority.URGENT, Instant.now().minusSeconds(60));
        });

        slaMonitor.checkSlas();
        slaMonitor.checkSlas(); // second run must not alert again

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            getAs(tech, "/api/notifications?unreadOnly=true")
                    .andExpect(jsonPath("$.content[?(@.type=='SLA_BREACHED')]").isNotEmpty());
            getAs(admin, "/api/notifications?unreadOnly=true")
                    .andExpect(jsonPath("$.content[?(@.type=='SLA_BREACHED')]").isNotEmpty());
        });

        getAs(admin, "/api/service-orders/" + order + "/timeline")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.action=='SLA_BREACHED')]", hasSize(1)));

        getAs(admin, "/api/service-orders?overdue=true")
                .andExpect(jsonPath("$.content[?(@.id=='" + order + "')]").isNotEmpty());
        getAs(admin, "/api/service-orders/" + order).andExpect(jsonPath("$.overdue").value(true));
    }

    @Test
    @DisplayName("Finished orders never raise SLA alerts")
    void finishedOrdersAreIgnored() throws Exception {
        Session admin = registerCompany("SLA Finished Co");
        UUID order = createOrder(admin, createCustomer(admin), null, "LOW");
        transition(admin, order, java.util.Map.of("targetStatus", "CANCELLED", "reason", "not needed"))
                .andExpect(status().isOk());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ServiceOrder o = orderRepository.findById(order).orElseThrow();
            o.changePriority(Priority.URGENT, Instant.now().minusSeconds(60));
        });

        slaMonitor.checkSlas();

        getAs(admin, "/api/service-orders/" + order + "/timeline")
                .andExpect(jsonPath("$.content[?(@.action=='SLA_BREACHED')]", hasSize(0)));
    }
}
