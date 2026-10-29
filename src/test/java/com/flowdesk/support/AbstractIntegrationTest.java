package com.flowdesk.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.user.Role;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Base class for end-to-end tests: real Spring context, real PostgreSQL (Testcontainers), real JWT
 * security. Every test registers its own companies, so tests never depend on each other's data.
 */
@SpringBootTest(properties = {
    "flowdesk.storage.path=${java.io.tmpdir}/flowdesk-it-storage",
    "flowdesk.sla.check-interval-ms=3600000"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
public abstract class AbstractIntegrationTest {

    protected static final String PASSWORD = "Password123";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper objectMapper;

    protected record Session(String token, UUID userId, UUID companyId, String email) {}

    // ---- Scenario helpers ---------------------------------------------------------------------

    protected Session registerCompany(String companyName) throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@test.dev";
        MvcResult result = mvc.perform(post("/api/auth/register-company")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "companyName", companyName, "adminName", "Admin", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = json(result);
        return new Session(
                body.get("accessToken").asText(),
                UUID.fromString(body.at("/user/id").asText()),
                UUID.fromString(body.at("/user/companyId").asText()),
                email);
    }

    protected Session createUser(Session admin, Role role) throws Exception {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.dev";
        postAs(admin, "/api/users", Map.of(
                        "name", role.name() + " User", "email", email, "password", PASSWORD, "role", role.name()))
                .andExpect(status().isCreated());
        MvcResult login = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = json(login);
        return new Session(
                body.get("accessToken").asText(), UUID.fromString(body.at("/user/id").asText()), admin.companyId(), email);
    }

    protected UUID createCustomer(Session session) throws Exception {
        MvcResult result = postAs(session, "/api/customers", Map.of("name", "Customer " + UUID.randomUUID()))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(json(result).get("id").asText());
    }

    protected JsonNode createOrderJson(Session session, UUID customerId, UUID assigneeId, String priority) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", "Printer not working");
        body.put("description", "Paper jam light is on");
        body.put("customerId", customerId.toString());
        body.put("priority", priority);
        if (assigneeId != null) {
            body.put("assigneeId", assigneeId.toString());
        }
        MvcResult result = postAs(session, "/api/service-orders", body).andExpect(status().isCreated()).andReturn();
        return json(result);
    }

    protected UUID createOrder(Session session, UUID customerId, UUID assigneeId, String priority) throws Exception {
        return UUID.fromString(createOrderJson(session, customerId, assigneeId, priority).get("id").asText());
    }

    protected ResultActions transition(Session session, UUID orderId, Map<String, ?> body) throws Exception {
        return postAs(session, "/api/service-orders/" + orderId + "/transitions", body);
    }

    // ---- HTTP helpers -------------------------------------------------------------------------

    protected JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder, Session session) {
        return builder.header("Authorization", "Bearer " + session.token());
    }

    protected ResultActions getAs(Session session, String url) throws Exception {
        return mvc.perform(auth(get(url), session));
    }

    protected ResultActions postAs(Session session, String url, Object body) throws Exception {
        return mvc.perform(auth(post(url), session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    protected ResultActions putAs(Session session, String url, Object body) throws Exception {
        return mvc.perform(auth(put(url), session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    protected ResultActions patchAs(Session session, String url, Object body) throws Exception {
        return mvc.perform(auth(patch(url), session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }
}
