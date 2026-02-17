package com.bellgado.calendar.integration;

import com.bellgado.calendar.api.dto.*;
import com.bellgado.calendar.domain.enums.CancelledBy;
import com.bellgado.calendar.domain.enums.SlotStatus;
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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SlotIntegrationTest {

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
    void bookSlot_happyPath() throws Exception {
        StudentResponse student = createStudent("Test Student");
        SlotResponse slot = createSlot(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).withNano(0));

        SlotBookRequest bookRequest = new SlotBookRequest(student.id(), "Test booking");

        mockMvc.perform(post("/slots/{slotId}/book", slot.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.student.id").value(student.id().toString()))
                .andExpect(jsonPath("$.student.fullName").value("Test Student"));
    }

    @Test
    void bookSlot_conflict_alreadyBooked() throws Exception {
        StudentResponse student1 = createStudent("Student One");
        StudentResponse student2 = createStudent("Student Two");
        SlotResponse slot = createSlot(OffsetDateTime.now(ZoneOffset.UTC).plusDays(2).withNano(0));

        SlotBookRequest bookRequest1 = new SlotBookRequest(student1.id(), null);
        mockMvc.perform(post("/slots/{slotId}/book", slot.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookRequest1)))
                .andExpect(status().isOk());

        SlotBookRequest bookRequest2 = new SlotBookRequest(student2.id(), null);
        mockMvc.perform(post("/slots/{slotId}/book", slot.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookRequest2)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @Test
    void cancelSlot_happyPath() throws Exception {
        StudentResponse student = createStudent("Cancel Test Student");
        SlotResponse slot = createSlot(OffsetDateTime.now(ZoneOffset.UTC).plusDays(3).withNano(0));

        SlotBookRequest bookRequest = new SlotBookRequest(student.id(), null);
        mockMvc.perform(post("/slots/{slotId}/book", slot.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookRequest)))
                .andExpect(status().isOk());

        SlotCancelRequest cancelRequest = new SlotCancelRequest(CancelledBy.STUDENT, "Sick");
        mockMvc.perform(post("/slots/{slotId}/cancel", slot.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void rescheduleSlot_happyPath() throws Exception {
        StudentResponse student = createStudent("Reschedule Test Student");
        SlotResponse originSlot = createSlot(OffsetDateTime.now(ZoneOffset.UTC).plusDays(4).withNano(0));
        SlotResponse targetSlot = createSlot(OffsetDateTime.now(ZoneOffset.UTC).plusDays(5).withNano(0));

        SlotBookRequest bookRequest = new SlotBookRequest(student.id(), "Original booking");
        mockMvc.perform(post("/slots/{slotId}/book", originSlot.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookRequest)))
                .andExpect(status().isOk());

        SlotRescheduleRequest rescheduleRequest = new SlotRescheduleRequest(targetSlot.id(), "Reschedule reason");
        mockMvc.perform(post("/slots/{slotId}/reschedule", originSlot.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rescheduleRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originSlot.status").value("FREE"))
                .andExpect(jsonPath("$.targetSlot.status").value("BOOKED"))
                .andExpect(jsonPath("$.targetSlot.student.id").value(student.id().toString()));
    }

    @Test
    void createSlot_conflict_duplicateTime() throws Exception {
        OffsetDateTime startAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(10).withNano(0);
        createSlot(startAt);

        SlotCreateRequest duplicateRequest = new SlotCreateRequest(startAt, 60);
        mockMvc.perform(post("/slots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void slotEvents_areRecorded() throws Exception {
        StudentResponse student = createStudent("Events Test Student");
        SlotResponse slot = createSlot(OffsetDateTime.now(ZoneOffset.UTC).plusDays(6).withNano(0));

        SlotBookRequest bookRequest = new SlotBookRequest(student.id(), null);
        mockMvc.perform(post("/slots/{slotId}/book", slot.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/slots/{slotId}/events", slot.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void deleteSlot_conflict_whenBooked() throws Exception {
        StudentResponse student = createStudent("Delete Test Student");
        SlotResponse slot = createSlot(OffsetDateTime.now(ZoneOffset.UTC).plusDays(7).withNano(0));

        SlotBookRequest bookRequest = new SlotBookRequest(student.id(), null);
        mockMvc.perform(post("/slots/{slotId}/book", slot.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/slots/{slotId}", slot.id()))
                .andExpect(status().isConflict())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void replaceSlot_happyPath() throws Exception {
        StudentResponse student1 = createStudent("Original Student");
        StudentResponse student2 = createStudent("Replacement Student");
        SlotResponse slot = createSlot(OffsetDateTime.now(ZoneOffset.UTC).plusDays(8).withNano(0));

        SlotBookRequest bookRequest = new SlotBookRequest(student1.id(), null);
        mockMvc.perform(post("/slots/{slotId}/book", slot.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookRequest)))
                .andExpect(status().isOk());

        SlotReplaceRequest replaceRequest = new SlotReplaceRequest(student2.id(), "Student swap");
        mockMvc.perform(post("/slots/{slotId}/replace", slot.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(replaceRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.student.id").value(student2.id().toString()));
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

    private SlotResponse createSlot(OffsetDateTime startAt) throws Exception {
        SlotCreateRequest request = new SlotCreateRequest(startAt, 60);
        MvcResult result = mockMvc.perform(post("/slots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), SlotResponse.class);
    }
}
