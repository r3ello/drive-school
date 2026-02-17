package com.bellgado.calendar.application.exception;

public class InvalidStateException extends ConflictException {
    public InvalidStateException(String message) {
        super(message);
    }
}
