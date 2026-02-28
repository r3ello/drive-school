package com.bellgado.calendar.api.dto.auth;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String tokenType,
        boolean passwordChangeRequired
) {
    public static LoginResponse of(String accessToken, String refreshToken,
                                   long expiresIn, boolean passwordChangeRequired) {
        return new LoginResponse(accessToken, refreshToken, expiresIn, "Bearer", passwordChangeRequired);
    }
}
