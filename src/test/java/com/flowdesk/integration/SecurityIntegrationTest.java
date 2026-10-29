package com.flowdesk.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowdesk.support.AbstractIntegrationTest;
import com.flowdesk.user.Role;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class SecurityIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("Protected endpoints require a valid token")
    void authenticationRequired() throws Exception {
        mvc.perform(get("/api/service-orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/service-orders").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Wrong credentials are rejected without revealing which part was wrong")
    void loginFailures() throws Exception {
        Session admin = registerCompany("Login Co");
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", admin.email(), "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "nobody@test.dev", "password", PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    @DisplayName("Input validation returns field-level errors")
    void validation() throws Exception {
        mvc.perform(post("/api/auth/register-company")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "companyName", "", "adminName", "A", "email", "not-an-email", "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.companyName").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    @DisplayName("Email addresses are unique across the platform")
    void duplicateEmail() throws Exception {
        Session admin = registerCompany("Duplicate Co");
        mvc.perform(post("/api/auth/register-company")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "companyName", "Other", "adminName", "Other", "email", admin.email().toUpperCase(), "password", PASSWORD))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Technicians have a restricted view and no management rights")
    void technicianRestrictions() throws Exception {
        Session admin = registerCompany("RBAC Co");
        Session tech = createUser(admin, Role.TECHNICIAN);
        UUID customer = createCustomer(admin);
        UUID mine = createOrder(admin, customer, tech.userId(), "LOW");
        UUID notMine = createOrder(admin, customer, null, "LOW");

        // No management endpoints.
        postAs(tech, "/api/service-orders",
                        Map.of("title", "x", "customerId", customer.toString(), "priority", "LOW"))
                .andExpect(status().isForbidden());
        postAs(tech, "/api/customers", Map.of("name", "New customer")).andExpect(status().isForbidden());
        getAs(tech, "/api/users").andExpect(status().isForbidden());
        getAs(tech, "/api/dashboard/summary").andExpect(status().isForbidden());
        putAs(tech, "/api/service-orders/" + mine + "/assignee", Map.of("technicianId", tech.userId().toString()))
                .andExpect(status().isForbidden());

        // Visibility is limited to their own orders.
        getAs(tech, "/api/service-orders").andExpect(jsonPath("$.totalElements").value(1));
        getAs(tech, "/api/service-orders/" + mine).andExpect(status().isOk());
        getAs(tech, "/api/service-orders/" + notMine).andExpect(status().isNotFound());
        transition(tech, notMine, Map.of("targetStatus", "IN_DIAGNOSIS")).andExpect(status().isNotFound());

        // Even when asking for someone else's orders explicitly.
        getAs(tech, "/api/service-orders?assigneeId=" + admin.userId()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("A deactivated user's token stops working immediately")
    void deactivatedUserIsLockedOut() throws Exception {
        Session admin = registerCompany("Deactivate Co");
        Session tech = createUser(admin, Role.TECHNICIAN);

        getAs(tech, "/api/auth/me").andExpect(status().isOk());
        patchAs(admin, "/api/users/" + tech.userId() + "/active", Map.of("active", false)).andExpect(status().isOk());
        getAs(tech, "/api/auth/me").andExpect(status().isUnauthorized());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", tech.email(), "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("An administrator cannot deactivate their own account")
    void cannotDeactivateSelf() throws Exception {
        Session admin = registerCompany("Self Co");
        patchAs(admin, "/api/users/" + admin.userId() + "/active", Map.of("active", false))
                .andExpect(status().isUnprocessableEntity());
    }
}
