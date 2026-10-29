package com.flowdesk.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowdesk.support.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

class AttachmentIntegrationTest extends AbstractIntegrationTest {

    private ResultActions upload(Session session, UUID order, MockMultipartFile file) throws Exception {
        return mvc.perform(auth(multipart("/api/service-orders/{id}/attachments", order).file(file), session));
    }

    @Test
    @DisplayName("Upload, list and download an attachment")
    void uploadListDownload() throws Exception {
        Session admin = registerCompany("Files Co");
        UUID order = createOrder(admin, createCustomer(admin), null, "LOW");

        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello flowdesk".getBytes(StandardCharsets.UTF_8));
        MvcResult uploaded = upload(admin, order, file)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename").value("notes.txt"))
                .andReturn();
        JsonNode body = json(uploaded);

        getAs(admin, "/api/service-orders/" + order + "/attachments")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        MvcResult download = getAs(admin, "/api/service-orders/" + order + "/attachments/" + body.get("id").asText())
                .andExpect(status().isOk())
                .andReturn();
        assertThat(download.getResponse().getContentAsString()).isEqualTo("hello flowdesk");
        assertThat(download.getResponse().getHeader("Content-Disposition")).contains("attachment");

        getAs(admin, "/api/service-orders/" + order + "/timeline")
                .andExpect(jsonPath("$.content[?(@.action=='ATTACHMENT_ADDED')]").isNotEmpty());
    }

    @Test
    @DisplayName("Dangerous file types are rejected and client file names are sanitised")
    void validation() throws Exception {
        Session admin = registerCompany("Files Validation Co");
        UUID order = createOrder(admin, createCustomer(admin), null, "LOW");

        upload(admin, order, new MockMultipartFile("file", "run.exe", "application/x-msdownload", new byte[] {1, 2, 3}))
                .andExpect(status().isUnprocessableEntity());
        upload(admin, order, new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]))
                .andExpect(status().isBadRequest());

        upload(admin, order, new MockMultipartFile("file", "../../etc/passwd.txt", "text/plain", "x".getBytes()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename").value("passwd.txt"));
    }

    @Test
    @DisplayName("Another company cannot download or upload to this order")
    void tenantIsolation() throws Exception {
        Session a = registerCompany("Files A");
        Session b = registerCompany("Files B");
        UUID order = createOrder(a, createCustomer(a), null, "LOW");

        MvcResult uploaded = upload(a, order, new MockMultipartFile("file", "secret.txt", "text/plain", "top secret".getBytes()))
                .andExpect(status().isCreated())
                .andReturn();
        String attachmentId = json(uploaded).get("id").asText();

        getAs(b, "/api/service-orders/" + order + "/attachments/" + attachmentId).andExpect(status().isNotFound());
        upload(b, order, new MockMultipartFile("file", "x.txt", "text/plain", "x".getBytes()))
                .andExpect(status().isNotFound());
    }
}
