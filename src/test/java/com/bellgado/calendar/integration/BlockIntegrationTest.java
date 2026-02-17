package com.bellgado.calendar.integration;

import com.bellgado.calendar.api.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BlockIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createBlock_happyPath() throws Exception {
        OffsetDateTime from = OffsetDateTime.now(ZoneOffset.UTC).plusDays(20).withHour(9).withMinute(0).withSecond(0).withNano(0);
        OffsetDateTime to = from.plusHours(4);

        BlockCreateRequest request = new BlockCreateRequest(from, to, "Vacation");

        mockMvc.perform(post("/blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.reason").value("Vacation"));
    }

    @Test
    void createBlock_validation_toBeforeFrom() throws Exception {
        OffsetDateTime from = OffsetDateTime.now(ZoneOffset.UTC).plusDays(21);
        OffsetDateTime to = from.minusHours(1);

        BlockCreateRequest request = new BlockCreateRequest(from, to, null);

        mockMvc.perform(post("/blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void listBlocks_happyPath() throws Exception {
        OffsetDateTime from = OffsetDateTime.now(ZoneOffset.UTC).plusDays(22).withHour(9).withMinute(0).withSecond(0).withNano(0);
        OffsetDateTime to = from.plusHours(2);

        BlockCreateRequest request = new BlockCreateRequest(from, to, "Test Block");
        mockMvc.perform(post("/blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/blocks")
                        .param("from", from.minusDays(1).toString())
                        .param("to", to.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void deleteBlock_happyPath() throws Exception {
        OffsetDateTime from = OffsetDateTime.now(ZoneOffset.UTC).plusDays(23).withHour(9).withMinute(0).withSecond(0).withNano(0);
        OffsetDateTime to = from.plusHours(2);

        BlockCreateRequest request = new BlockCreateRequest(from, to, "To Delete");
        MvcResult result = mockMvc.perform(post("/blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        BlockResponse created = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                BlockResponse.class
        );

        mockMvc.perform(delete("/blocks/{id}", created.id()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteBlock_notFound() throws Exception {
        mockMvc.perform(delete("/blocks/{id}", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"));
    }
}
