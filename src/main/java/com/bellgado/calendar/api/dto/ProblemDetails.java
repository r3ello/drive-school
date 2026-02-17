package com.bellgado.calendar.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetails(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        List<FieldError> errors
) {
    public static ProblemDetails of(String type, String title, int status, String detail, String instance) {
        return new ProblemDetails(type, title, status, detail, instance, null);
    }

    public static ProblemDetails withErrors(String type, String title, int status, String detail, String instance, List<FieldError> errors) {
        return new ProblemDetails(type, title, status, detail, instance, errors);
    }
}
