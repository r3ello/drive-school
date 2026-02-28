package com.bellgado.calendar.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthEmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.from:noreply@calendar.local}")
    private String fromAddress;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public void sendInvitation(String toEmail, String fullName,
                               String tempPassword, String confirmationToken) {
        String confirmationLink = baseUrl + "/api/v1/auth/confirm-email?token=" + confirmationToken;

        String body = """
                Hello %s,

                Your account has been created for the Class Scheduling Calendar.

                Your temporary password is: %s

                Please confirm your email and set a new password by clicking the link below:
                %s

                This link will expire in 72 hours.

                If you did not expect this email, please ignore it.
                """.formatted(fullName, tempPassword, confirmationLink);

        send(toEmail, "Your Calendar Account — Confirm your email", body);
    }

    private void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Auth email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send auth email to {}: {}", to, e.getMessage());
            // Do not rethrow — email failure should not block the invite response.
            // The teacher can re-invite if the student reports they didn't receive it.
        }
    }
}
