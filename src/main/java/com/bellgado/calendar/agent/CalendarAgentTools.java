package com.bellgado.calendar.agent;

import com.bellgado.calendar.api.dto.*;
import com.bellgado.calendar.application.service.BlockService;
import com.bellgado.calendar.application.service.SlotService;
import com.bellgado.calendar.application.service.StudentService;
import com.bellgado.calendar.application.service.WaitlistService;
import com.bellgado.calendar.domain.enums.CancelledBy;
import com.bellgado.calendar.domain.enums.DayOfWeek;
import com.bellgado.calendar.domain.enums.SlotStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class CalendarAgentTools {

    private final SlotService slotService;
    private final StudentService studentService;
    private final BlockService blockService;
    private final WaitlistService waitlistService;
    private final ObjectMapper objectMapper;

    // ========================================================================
    // SLOT TOOLS
    // ========================================================================

    @Tool(description = "Create a single lesson slot at a specific date and time. " +
            "The startAt must be in ISO-8601 OffsetDateTime format (e.g. '2025-03-10T14:00:00+02:00'). " +
            "Returns the created slot with its ID and status FREE.")
    public String createSlot(
            @ToolParam(description = "Slot start time in ISO-8601 format, e.g. '2025-03-10T14:00:00+02:00'") String startAt) {
        try {
            SlotCreateRequest request = new SlotCreateRequest(OffsetDateTime.parse(startAt), null);
            SlotResponse response = slotService.create(request);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.warn("Tool call failed [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    @Tool(description = "Bulk-generate lesson slots from weekly recurring rules over a date range. " +
            "Each weeklyRule has: dayOfWeek (MONDAY-SUNDAY), startTime (HH:mm), endTime (HH:mm). " +
            "Returns count of created and skipped (already existing) slots.")
    public String generateSlots(
            @ToolParam(description = "Start date in yyyy-MM-dd format") String from,
            @ToolParam(description = "End date in yyyy-MM-dd format") String to,
            @ToolParam(description = "Timezone, e.g. 'Europe/Sofia'") String timezone,
            @ToolParam(description = "JSON array of weekly rules, e.g. [{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"09:00\",\"endTime\":\"12:00\"}]") String weeklyRulesJson) {
        try {
            List<WeeklyRule> rules = objectMapper.readValue(weeklyRulesJson, new TypeReference<>() {});
            SlotGenerateRequest request = new SlotGenerateRequest(
                    LocalDate.parse(from), LocalDate.parse(to), timezone, rules, null);
            SlotGenerateResponse response = slotService.generate(request);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.warn("Tool call failed [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    @Tool(description = "List lesson slots within a date range, optionally filtered by status. " +
            "Statuses: FREE, BOOKED, CANCELLED, BLOCKED. " +
            "Returns a list of slots with their ID, time, status, and assigned student if booked.")
    public String listSlots(
            @ToolParam(description = "Range start in ISO-8601 format") String from,
            @ToolParam(description = "Range end in ISO-8601 format") String to,
            @ToolParam(description = "Comma-separated statuses to filter, e.g. 'FREE,BOOKED'. Empty string means all statuses.") String statuses) {
        try {
            Collection<SlotStatus> statusList = parseStatuses(statuses);
            List<SlotResponse> response = slotService.list(
                    OffsetDateTime.parse(from), OffsetDateTime.parse(to), statusList);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.warn("Tool call failed [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    @Tool(description = "List lesson slots for a specific student within a date range. " +
            "Useful to see a student's upcoming or past lessons.")
    public String listSlotsByStudent(
            @ToolParam(description = "Student UUID") String studentId,
            @ToolParam(description = "Range start in ISO-8601 format") String from,
            @ToolParam(description = "Range end in ISO-8601 format") String to,
            @ToolParam(description = "Comma-separated statuses to filter. Empty string means all.") String statuses) {
        try {
            Collection<SlotStatus> statusList = parseStatuses(statuses);
            List<SlotResponse> response = slotService.listByStudent(
                    UUID.fromString(studentId),
                    OffsetDateTime.parse(from), OffsetDateTime.parse(to), statusList);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.warn("Tool call failed [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    @Tool(description = "Get a single slot by its UUID. Returns full slot details including student info if booked.")
    public String getSlotById(
            @ToolParam(description = "Slot UUID") String slotId) {
        try {
            SlotResponse response = slotService.getById(UUID.fromString(slotId));
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.warn("Tool call failed [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    @Tool(description = "Delete a slot by its UUID. Cannot delete BOOKED slots - cancel or free them first.")
    public String deleteSlot(
            @ToolParam(description = "Slot UUID to delete") String slotId) {
        try {
            slotService.delete(UUID.fromString(slotId));
            return "Slot " + slotId + " deleted successfully.";
        } catch (Exception e) {
            log.warn("Tool call failed [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    @Tool(description = "Book a FREE slot for a student. The slot must be in FREE status. " +
            "Assigns the student to the slot and changes status to BOOKED.")
    public String bookSlot(
            @ToolParam(description = "Slot UUID to book") String slotId,
            @ToolParam(description = "Student UUID to assign") String studentId,
            @ToolParam(description = "Optional booking notes, empty string if none") String notes) {
        try {
            SlotBookRequest request = new SlotBookRequest(
                    UUID.fromString(studentId),
                    notes != null && !notes.isBlank() ? notes : null);
            SlotResponse response = slotService.book(UUID.fromString(slotId), request);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.warn("Tool call failed [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    @Tool(description = "Cancel a BOOKED slot. Specify who cancelled (STUDENT or TEACHER) and an optional reason. " +
            "The slot moves to CANCELLED status but keeps the student reference.")
    public String cancelSlot(
            @ToolParam(description = "Slot UUID to cancel") String slotId,
            @ToolParam(description = "Who cancelled: STUDENT or TEACHER") String cancelledBy,
            @ToolParam(description = "Optional cancellation reason, empty string if none") String reason) {
        try {
            SlotCancelRequest request = new SlotCancelRequest(
                    CancelledBy.valueOf(cancelledBy.toUpperCase()),
                    reason != null && !reason.isBlank() ? reason : null);
            SlotResponse response = slotService.cancel(UUID.fromString(slotId), request);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.warn("Tool call failed [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    @Tool(description = "Free a CANCELLED or BOOKED slot. Removes the student and sets status to FREE, " +
            "making it available for new bookings.")
    public String freeSlot(
            @ToolParam(description = "Slot UUID to free") String slotId,
            @ToolParam(description = "Optional notes, empty string if none") String notes) {
        try {
            SlotFreeRequest request = new SlotFreeRequest(
                    notes != null && !notes.isBlank() ? notes : null);
            SlotResponse response = slotService.free(UUID.fromString(slotId), request);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.warn("Tool call failed [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    @Tool(description = "Replace the student on a BOOKED or CANCELLED slot with a different student. " +
            "If the slot was CANCELLED, it becomes BOOKED again with the new student.")
    public String replaceStudentOnSlot(
            @ToolParam(description = "Slot UUID") String slotId,
            @ToolParam(description = "New student UUID") String newStudentId,
            @ToolParam(description = "Optional reason for the replacement, empty string if none") String reason) {
        try {
            SlotReplaceRequest request = new SlotReplaceRequest(
                    UUID.fromString(newStudentId),
                    reason != null && !reason.isBlank() ? reason : null);
            SlotResponse response = slotService.replace(UUID.fromString(slotId), request);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.warn("Tool call failed [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    @Tool(description = "Reschedule a booking from one slot to another. The origin slot must be BOOKED " +
            "and the target slot must be FREE. The student and notes are moved. " +
            "Origin becomes FREE, target becomes BOOKED.")
    public String rescheduleSlot(
            @ToolParam(description = "Origin slot UUID (currently BOOKED)") String originSlotId,
            @ToolParam(description = "Target slot UUID (must be FREE)") String targetSlotId,
            @ToolParam(description = "Optional reason for rescheduling, empty string if none") String reason) {
        try {
            SlotRescheduleRequest request = new SlotRescheduleRequest(
                    UUID.fromString(targetSlotId),
                    reason != null && !reason.isBlank() ? reason : null);
            RescheduleResponse response = slotService.reschedule(UUID.fromString(originSlotId), request);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.warn("Tool call failed [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    // ========================================================================
    // STUDENT TOOLS
    // ========================================================================

    @Tool(description = "Create a new student. fullName and phone are required before calling this tool. " +
            "Collect them from the user first. phone must contain only digits, spaces, dashes, dots, " +
            "parentheses, or a leading + (e.g. '0888 123 456' or '+359888123456'). " +
            "Returns the created student with their UUID.")
    public String createStudent(
            @ToolParam(description = "Student's full name (required)") String fullName,
            @ToolParam(description = "Display phone number — digits, spaces, dashes, dots, parens, optional leading +") String phone,
            @ToolParam(description = "Email address, empty string if none") String email,
            @ToolParam(description = "Notes about the student, empty string if none") String notes) {
        try {
            StudentCreateRequest request = new StudentCreateRequest(
                    fullName,
                    phone != null && !phone.isBlank() ? phone : null,
                    email != null && !email.isBlank() ? email : null,
                    notes != null && !notes.isBlank() ? notes : null,
                    null, null, null, null, null, null, null, null);
            StudentResponse response = studentService.create(request);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.warn("Tool call failed [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    @Tool(description = "Search students by name, phone, or email. " +
            "Returns a list of matching students. Use this to find a student's UUID before booking.")
    public String searchStudents(
            @ToolParam(description = "Search query to match against name, phone, or email. Can be empty string for all.") String query,
            @ToolParam(description = "Filter by active status: 'true', 'false', or empty string for all") String active) {
        try {
            Boolean activeFilter = null;
            if (active != null && !active.isBlank()) {
                activeFilter = Boolean.parseBoolean(active);
            }
            var response = studentService.list(
                    query != null && !query.isBlank() ? query : null,
                    activeFilter,
                    Pageable.ofSize(20));
            return objectMapper.writeValueAsString(response.getContent());
        } catch (Exception e) {
            log.warn("Tool call failed [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    @Tool(description = "Get a student's full details by their UUID.")
    public String getStudentById(
            @ToolParam(description = "Student UUID") String studentId) {
        try {
            StudentResponse response = studentService.getById(UUID.fromString(studentId));
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.warn("Tool call failed [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    @Tool(description = "Deactivate a student. They will no longer appear in active student searches.")
    public String deactivateStudent(
            @ToolParam(description = "Student UUID to deactivate") String studentId) {
        try {
            studentService.deactivate(UUID.fromString(studentId));
            return "Student " + studentId + " deactivated successfully.";
        } catch (Exception e) {
            log.warn("Tool call failed [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    // ========================================================================
    // BLOCK TOOLS
    // ========================================================================

    @Tool(description = "Block a single FREE or CANCELLED slot by its UUID, making it BLOCKED (unavailable). " +
            "Prefer this over createBlock for blocking individual time slots. " +
            "BOOKED slots cannot be blocked — cancel them first.")
    public String blockSlot(
            @ToolParam(description = "Slot UUID to block") String slotId) {
        try {
            SlotResponse response = slotService.blockSlot(UUID.fromString(slotId));
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.warn("Tool call failed [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    @Tool(description = "Unblock a single BLOCKED slot by its UUID, restoring it to FREE status. " +
            "Only unblocks this one slot — other slots in the same block group are not affected.")
    public String unblockSlot(
            @ToolParam(description = "Slot UUID to unblock") String slotId) {
        try {
            SlotResponse response = slotService.unblockSlot(UUID.fromString(slotId));
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.warn("Tool call failed [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    @Tool(description = "Create a time block (vacation, holiday, unavailable period). " +
            "All non-BOOKED slots in the range are blocked. New BLOCKED slots are created for empty hours. " +
            "BOOKED slots are not affected. " +
            "NOTE: To block a single individual slot use blockSlot(slotId) instead.")
    public String createBlock(
            @ToolParam(description = "Block start in ISO-8601 format") String from,
            @ToolParam(description = "Block end in ISO-8601 format") String to,
            @ToolParam(description = "Reason for the block, e.g. 'vacation', empty string if none") String reason) {
        try {
            BlockCreateRequest request = new BlockCreateRequest(
                    OffsetDateTime.parse(from), OffsetDateTime.parse(to),
                    reason != null && !reason.isBlank() ? reason : null);
            BlockResponse response = blockService.create(request);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.warn("Tool call failed [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    @Tool(description = "List time blocks overlapping a date range. " +
            "Returns blocks with their ID, date range, and reason.")
    public String listBlocks(
            @ToolParam(description = "Range start in ISO-8601 format") String from,
            @ToolParam(description = "Range end in ISO-8601 format") String to) {
        try {
            List<BlockResponse> response = blockService.list(
                    OffsetDateTime.parse(from), OffsetDateTime.parse(to));
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.warn("Tool call failed [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    @Tool(description = "Delete a time block and unblock all slots that were blocked by it. " +
            "Blocked slots return to FREE status.")
    public String deleteBlock(
            @ToolParam(description = "Block UUID to delete") String blockId) {
        try {
            blockService.delete(UUID.fromString(blockId));
            return "Block " + blockId + " deleted and slots unblocked successfully.";
        } catch (Exception e) {
            log.warn("Tool call failed [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    // ========================================================================
    // WAITLIST TOOLS
    // ========================================================================

    @Tool(description = "Add a student to the waitlist with preferred days and time ranges. " +
            "Days: MONDAY-SUNDAY. Time ranges in HH:mm format. Priority: lower number = higher priority (default 0).")
    public String addToWaitlist(
            @ToolParam(description = "Student UUID") String studentId,
            @ToolParam(description = "Comma-separated preferred days, e.g. 'MONDAY,WEDNESDAY'. Empty string if no preference.") String preferredDays,
            @ToolParam(description = "JSON array of time ranges, e.g. [{\"from\":\"09:00\",\"to\":\"12:00\"}]. Empty string if no preference.") String preferredTimeRangesJson,
            @ToolParam(description = "Notes about the waitlist request, empty string if none") String notes,
            @ToolParam(description = "Priority number (0 = highest). Default is 0.") String priority) {
        try {
            List<DayOfWeek> days = null;
            if (preferredDays != null && !preferredDays.isBlank()) {
                days = Arrays.stream(preferredDays.split(","))
                        .map(String::trim)
                        .map(d -> DayOfWeek.valueOf(d.toUpperCase()))
                        .collect(Collectors.toList());
            }

            List<TimeRange> timeRanges = null;
            if (preferredTimeRangesJson != null && !preferredTimeRangesJson.isBlank()) {
                timeRanges = objectMapper.readValue(preferredTimeRangesJson, new TypeReference<>() {});
            }

            int prio = 0;
            if (priority != null && !priority.isBlank()) {
                prio = Integer.parseInt(priority);
            }

            WaitlistCreateRequest request = new WaitlistCreateRequest(
                    UUID.fromString(studentId), days, timeRanges,
                    notes != null && !notes.isBlank() ? notes : null,
                    prio);
            WaitlistResponse response = waitlistService.add(request);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.warn("Tool call failed [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    @Tool(description = "Remove a student from the waitlist. The entry is marked inactive, not deleted.")
    public String removeFromWaitlist(
            @ToolParam(description = "Waitlist entry UUID") String waitlistItemId) {
        try {
            waitlistService.remove(UUID.fromString(waitlistItemId));
            return "Waitlist entry " + waitlistItemId + " removed successfully.";
        } catch (Exception e) {
            log.warn("Tool call failed [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private Collection<SlotStatus> parseStatuses(String statuses) {
        if (statuses == null || statuses.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(statuses.split(","))
                .map(String::trim)
                .map(s -> SlotStatus.valueOf(s.toUpperCase()))
                .collect(Collectors.toList());
    }
}
