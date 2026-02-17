package com.bellgado.calendar.integration;

import com.bellgado.calendar.api.dto.*;
import com.bellgado.calendar.domain.enums.DayOfWeek;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SlotGenerationIntegrationTest {

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
    void generateSlots_happyPath() throws Exception {
        LocalDate from = LocalDate.now().plusDays(30);
        LocalDate to = from.plusDays(7);

        SlotGenerateRequest request = new SlotGenerateRequest(
                from,
                to,
                "Europe/Sofia",
                List.of(
                        new WeeklyRule(DayOfWeek.MONDAY, "09:00", "12:00"),
                        new WeeklyRule(DayOfWeek.WEDNESDAY, "14:00", "18:00")
                ),
                60
        );

        mockMvc.perform(post("/slots/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").isNumber())
                .andExpect(jsonPath("$.skippedCount").isNumber());
    }

    @Test
    void generateSlots_validation_emptyRules() throws Exception {
        LocalDate from = LocalDate.now().plusDays(40);
        LocalDate to = from.plusDays(7);

        String requestJson = """
            {
                "from": "%s",
                "to": "%s",
                "timezone": "Europe/Sofia",
                "weeklyRules": [],
                "slotDurationMinutes": 60
            }
            """.formatted(from, to);

        mockMvc.perform(post("/slots/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void generateSlots_skipsExistingSlots() throws Exception {
        LocalDate from = LocalDate.now().plusDays(50);
        LocalDate to = from.plusDays(7);

        SlotGenerateRequest request = new SlotGenerateRequest(
                from,
                to,
                "Europe/Sofia",
                List.of(new WeeklyRule(DayOfWeek.FRIDAY, "10:00", "12:00")),
                60
        );

        mockMvc.perform(post("/slots/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").isNumber());

        mockMvc.perform(post("/slots/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skippedCount").isNumber());
    }

    @Test
    void listSlots_filterByStatus() throws Exception {
        LocalDate from = LocalDate.now().plusDays(60);
        LocalDate to = from.plusDays(7);

        SlotGenerateRequest request = new SlotGenerateRequest(
                from,
                to,
                "Europe/Sofia",
                List.of(new WeeklyRule(DayOfWeek.TUESDAY, "09:00", "11:00")),
                60
        );

        mockMvc.perform(post("/slots/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        OffsetDateTime queryFrom = from.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime queryTo = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        mockMvc.perform(get("/slots")
                        .param("from", queryFrom.toString())
                        .param("to", queryTo.toString())
                        .param("status", "FREE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
