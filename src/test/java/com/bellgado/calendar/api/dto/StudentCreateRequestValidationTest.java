package com.bellgado.calendar.api.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Bean Validation constraints on {@link StudentCreateRequest}.
 * No Spring context is needed — uses the Jakarta Validation API directly.
 */
class StudentCreateRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        factory.close();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Builds a minimal valid request and returns violations scoped to the phone field. */
    private Set<ConstraintViolation<StudentCreateRequest>> validatePhone(String phone) {
        StudentCreateRequest req = new StudentCreateRequest(
                "John Doe", phone, null, null,
                null, null, null, null, null, null, null, null);
        return validator.validate(req).stream()
                .filter(v -> v.getPropertyPath().toString().equals("phone"))
                .collect(Collectors.toSet());
    }

    /** Builds a request and returns ALL violations. */
    private Set<ConstraintViolation<StudentCreateRequest>> validateAll(String fullName, String phone, String email) {
        StudentCreateRequest req = new StudentCreateRequest(
                fullName, phone, email, null,
                null, null, null, null, null, null, null, null);
        return validator.validate(req);
    }

    // =========================================================================
    // Phone — invalid formats (must produce violations)
    // =========================================================================

    @Test
    void phone_shouldRejectLetters() {
        assertFalse(validatePhone("abc").isEmpty(), "Letters in phone must be rejected");
    }

    @Test
    void phone_shouldRejectAtSign() {
        assertFalse(validatePhone("phone@example.com").isEmpty(), "@ symbol must be rejected");
    }

    @Test
    void phone_shouldRejectSemicolon() {
        assertFalse(validatePhone("0888;123456").isEmpty(), "Semicolon must be rejected");
    }

    @Test
    void phone_shouldRejectSlash() {
        assertFalse(validatePhone("0888/123456").isEmpty(), "Slash must be rejected");
    }

    @Test
    void phone_shouldRejectExceedingMaxLength() {
        // 51 digits exceeds @Size(max=50)
        assertFalse(validatePhone("1".repeat(51)).isEmpty(), "Phone longer than 50 chars must be rejected");
    }

    // =========================================================================
    // Phone — valid formats (must produce no violations)
    // =========================================================================

    @Test
    void phone_shouldAcceptDigitsOnly() {
        assertTrue(validatePhone("0888123456").isEmpty(), "Digits-only phone must be valid");
    }

    @Test
    void phone_shouldAcceptDigitsWithSpaces() {
        assertTrue(validatePhone("0888 123 456").isEmpty(), "Phone with spaces must be valid");
    }

    @Test
    void phone_shouldAcceptLeadingPlus() {
        assertTrue(validatePhone("+359888123456").isEmpty(), "Phone with leading + must be valid");
    }

    @Test
    void phone_shouldAcceptDashes() {
        assertTrue(validatePhone("0888-123-456").isEmpty(), "Phone with dashes must be valid");
    }

    @Test
    void phone_shouldAcceptDots() {
        assertTrue(validatePhone("0888.123.456").isEmpty(), "Phone with dots must be valid");
    }

    @Test
    void phone_shouldAcceptParentheses() {
        assertTrue(validatePhone("(02) 987654").isEmpty(), "Phone with parentheses must be valid");
    }

    @Test
    void phone_shouldAcceptMixedFormatWithPlus() {
        assertTrue(validatePhone("+359 (0) 88-12.34 56").isEmpty(), "Mixed valid format must be accepted");
    }

    @Test
    void phone_shouldAcceptNull() {
        // phone is optional — null should not trigger the @Pattern constraint
        assertTrue(validatePhone(null).isEmpty(), "Null phone must be valid (field is optional)");
    }

    @Test
    void phone_shouldAcceptExactMaxLength() {
        // exactly 50 chars — boundary value
        assertTrue(validatePhone("1".repeat(50)).isEmpty(), "Phone of exactly 50 digits must be valid");
    }

    // =========================================================================
    // fullName — required field smoke tests
    // =========================================================================

    @Test
    void fullName_shouldRejectBlank() {
        Set<ConstraintViolation<StudentCreateRequest>> violations = validateAll("", null, null);
        boolean hasFullNameViolation = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("fullName"));
        assertTrue(hasFullNameViolation, "Blank fullName must produce a violation");
    }

    @Test
    void fullName_shouldRejectNull() {
        Set<ConstraintViolation<StudentCreateRequest>> violations = validateAll(null, null, null);
        boolean hasFullNameViolation = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("fullName"));
        assertTrue(hasFullNameViolation, "Null fullName must produce a violation");
    }

    // =========================================================================
    // email — format validation
    // =========================================================================

    @Test
    void email_shouldRejectInvalidFormat() {
        Set<ConstraintViolation<StudentCreateRequest>> violations = validateAll("John Doe", null, "not-an-email");
        boolean hasEmailViolation = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("email"));
        assertTrue(hasEmailViolation, "Invalid email format must produce a violation");
    }

    @Test
    void email_shouldAcceptValidFormat() {
        Set<ConstraintViolation<StudentCreateRequest>> violations = validateAll("John Doe", null, "john@example.com");
        boolean hasEmailViolation = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("email"));
        assertFalse(hasEmailViolation, "Valid email must not produce a violation");
    }

    @Test
    void email_shouldAcceptNull() {
        Set<ConstraintViolation<StudentCreateRequest>> violations = validateAll("John Doe", null, null);
        boolean hasEmailViolation = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("email"));
        assertFalse(hasEmailViolation, "Null email must be valid (field is optional)");
    }
}
