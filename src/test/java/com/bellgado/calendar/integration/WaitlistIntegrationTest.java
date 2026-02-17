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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class WaitlistIntegrationTest {

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
    void addToWaitlist_happyPath() throws Exception {
        StudentResponse student = createStudent("Waitlist Student");

        WaitlistCreateRequest request = new WaitlistCreateRequest(
                student.id(),
                List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                List.of(new TimeRange("16:00", "20:00")),
                "Prefers evening classes",
                1
        );

        mockMvc.perform(post("/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.student.id").value(student.id().toString()))
                .andExpect(jsonPath("$.priority").value(1))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void addToWaitlist_studentNotFound() throws Exception {
        WaitlistCreateRequest request = new WaitlistCreateRequest(
                java.util.UUID.randomUUID(),
                null,
                null,
                null,
                0
        );

        mockMvc.perform(post("/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void listWaitlist_happyPath() throws Exception {
        StudentResponse student = createStudent("List Waitlist Student");

        WaitlistCreateRequest request = new WaitlistCreateRequest(
                student.id(),
                null,
                null,
                "Test entry",
                0
        );

        mockMvc.perform(post("/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/waitlist")
                        .param("active", "true")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void removeFromWaitlist_happyPath() throws Exception {
        StudentResponse student = createStudent("Remove Waitlist Student");

        WaitlistCreateRequest request = new WaitlistCreateRequest(
                student.id(),
                null,
                null,
                null,
                0
        );

        MvcResult result = mockMvc.perform(post("/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        WaitlistResponse created = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                WaitlistResponse.class
        );

        mockMvc.perform(delete("/waitlist/{id}", created.id()))
                .andExpect(status().isNoContent());
    }

    @Test
    void removeFromWaitlist_notFound() throws Exception {
        mockMvc.perform(delete("/waitlist/{id}", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"));
    }

    private StudentResponse createStudent(String name) throws Exception {
        StudentCreateRequest request = new StudentCreateRequest(
                name, null, null, null,
                null, null, null, null, null, null, null, null
        );
        MvcResult result = mockMvc.perform(post("/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), StudentResponse.class);
    }
}
