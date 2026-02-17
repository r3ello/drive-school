package com.bellgado.calendar.integration;

import com.bellgado.calendar.api.dto.StudentCreateRequest;
import com.bellgado.calendar.api.dto.StudentResponse;
import com.bellgado.calendar.api.dto.StudentUpdateRequest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class StudentIntegrationTest {

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
    void createStudent_happyPath() throws Exception {
        StudentCreateRequest request = new StudentCreateRequest(
                "John Doe",
                "+1234567890",
                "john@example.com",
                "Test notes",
                null, null, null, null, null, null, null, null
        );

        mockMvc.perform(post("/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.fullName").value("John Doe"))
                .andExpect(jsonPath("$.phone").value("+1234567890"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.notes").value("Test notes"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createStudent_validation_emptyName() throws Exception {
        StudentCreateRequest request = new StudentCreateRequest(
                "", null, null, null,
                null, null, null, null, null, null, null, null
        );

        mockMvc.perform(post("/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void updateStudent_happyPath() throws Exception {
        StudentCreateRequest createRequest = new StudentCreateRequest(
                "Initial Name", null, null, null,
                null, null, null, null, null, null, null, null
        );
        MvcResult createResult = mockMvc.perform(post("/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        StudentResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                StudentResponse.class
        );

        StudentUpdateRequest updateRequest = new StudentUpdateRequest(
                "Updated Name",
                "+9876543210",
                "updated@example.com",
                "Updated notes",
                true,
                null, null, null, null, null, null, null, null
        );

        mockMvc.perform(put("/students/{id}", created.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Name"))
                .andExpect(jsonPath("$.phone").value("+9876543210"))
                .andExpect(jsonPath("$.email").value("updated@example.com"));
    }

    @Test
    void deactivateStudent_happyPath() throws Exception {
        StudentCreateRequest request = new StudentCreateRequest(
                "Deactivate Me", null, null, null,
                null, null, null, null, null, null, null, null
        );
        MvcResult result = mockMvc.perform(post("/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        StudentResponse created = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                StudentResponse.class
        );

        mockMvc.perform(patch("/students/{id}/deactivate", created.id()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/students/{id}", created.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void listStudents_withPagination() throws Exception {
        for (int i = 0; i < 5; i++) {
            StudentCreateRequest request = new StudentCreateRequest(
                    "Pagination Test " + i, null, null, null,
                    null, null, null, null, null, null, null, null
            );
            mockMvc.perform(post("/students")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/students")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void getStudent_notFound() throws Exception {
        mockMvc.perform(get("/students/{id}", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404));
    }
}
