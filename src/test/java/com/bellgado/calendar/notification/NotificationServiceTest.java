package com.bellgado.calendar.notification;

import com.bellgado.calendar.application.exception.NotFoundException;
import com.bellgado.calendar.domain.entity.Notification;
import com.bellgado.calendar.domain.entity.Student;
import com.bellgado.calendar.domain.enums.NotificationChannel;
import com.bellgado.calendar.domain.enums.NotificationStatus;
import com.bellgado.calendar.domain.enums.NotificationType;
import com.bellgado.calendar.infrastructure.repository.NotificationRepository;
import com.bellgado.calendar.infrastructure.repository.StudentRepository;
import com.bellgado.calendar.notification.dto.NotificationCreateRequest;
import com.bellgado.calendar.notification.dto.NotificationResponse;
import com.bellgado.calendar.notification.provider.SendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private NotificationDispatcher dispatcher;

    private NotificationProperties properties;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        properties.setEnabled(true);
        properties.getDefaults().setMaxAttempts(3);
        properties.getDefaults().setExpiry(Duration.ofHours(24));
        properties.getDefaults().setPriority(0);

        notificationService = new NotificationService(
                notificationRepository,
                studentRepository,
                dispatcher,
                properties,
                new NotificationMessageFactory()
        );
    }

    private Student createEligibleStudent() {
        Student student = new Student("John Doe", "+1 (202) 555-1234", "john@example.com", null);
        student.setId(UUID.randomUUID());
        student.setActive(true);
        student.setNotificationOptIn(true);
        student.setPreferredNotificationChannel(NotificationChannel.EMAIL);
        student.setPhoneE164("+12025551234");
        return student;
    }

    @Nested
    class CreateOutboxRecords {

        @Test
        void shouldCreatePendingNotificationForEligibleStudent() {
            Student student = createEligibleStudent();
            UUID studentId = student.getId();

            when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
            when(dispatcher.hasProvider(NotificationChannel.EMAIL)).thenReturn(true);
            when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> {
                Notification n = i.getArgument(0);
                n.setId(UUID.randomUUID());
                return n;
            });

            NotificationCreateRequest request = new NotificationCreateRequest(
                    studentId,
                    NotificationChannel.EMAIL,
                    NotificationType.CLASS_SCHEDULED,
                    null,
                    Map.of("className", "Piano Lesson"),
                    null,
                    null,
                    null,
                    null
            );

            NotificationResponse response = notificationService.create(request);

            assertNotNull(response);
            assertEquals(NotificationStatus.PENDING, response.status());
            assertEquals(NotificationChannel.EMAIL, response.channel());
            assertEquals(NotificationType.CLASS_SCHEDULED, response.type());

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());

            Notification saved = captor.getValue();
            assertEquals(studentId, saved.getStudentId());
            assertEquals(NotificationChannel.EMAIL, saved.getChannel());
            assertEquals(NotificationType.CLASS_SCHEDULED, saved.getType());
            assertEquals("CLASS_SCHEDULED", saved.getTemplateKey());
            assertEquals(3, saved.getMaxAttempts());
            assertNotNull(saved.getExpiresAt());
        }

        @Test
        void shouldUseStudentPreferredChannelWhenNotSpecified() {
            Student student = createEligibleStudent();
            student.setPreferredNotificationChannel(NotificationChannel.SMS);
            UUID studentId = student.getId();

            when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
            when(dispatcher.hasProvider(NotificationChannel.SMS)).thenReturn(true);
            when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> {
                Notification n = i.getArgument(0);
                n.setId(UUID.randomUUID());
                return n;
            });

            NotificationCreateRequest request = NotificationCreateRequest.simple(
                    studentId,
                    NotificationType.CLASS_REMINDER,
                    Map.of("time", "10:00 AM")
            );

            NotificationResponse response = notificationService.create(request);

            assertNotNull(response);
            assertEquals(NotificationChannel.SMS, response.channel());
        }

        @Test
        void shouldSetCustomTemplateKeyWhenProvided() {
            Student student = createEligibleStudent();
            UUID studentId = student.getId();

            when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
            when(dispatcher.hasProvider(NotificationChannel.EMAIL)).thenReturn(true);
            when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> {
                Notification n = i.getArgument(0);
                n.setId(UUID.randomUUID());
                return n;
            });

            NotificationCreateRequest request = new NotificationCreateRequest(
                    studentId,
                    NotificationChannel.EMAIL,
                    NotificationType.CUSTOM,
                    "WELCOME_MESSAGE",
                    Map.of("promoCode", "SUMMER2024"),
                    null,
                    null,
                    null,
                    null
            );

            notificationService.create(request);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            assertEquals("WELCOME_MESSAGE", captor.getValue().getTemplateKey());
        }

        @Test
        void shouldSetScheduledForWhenProvided() {
            Student student = createEligibleStudent();
            UUID studentId = student.getId();
            OffsetDateTime scheduledTime = OffsetDateTime.now().plusHours(2);

            when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
            when(dispatcher.hasProvider(NotificationChannel.EMAIL)).thenReturn(true);
            when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> {
                Notification n = i.getArgument(0);
                n.setId(UUID.randomUUID());
                return n;
            });

            NotificationCreateRequest request = new NotificationCreateRequest(
                    studentId,
                    NotificationChannel.EMAIL,
                    NotificationType.CLASS_REMINDER,
                    null,
                    null,
                    null,
                    null,
                    scheduledTime,
                    null
            );

            notificationService.create(request);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            assertEquals(scheduledTime, captor.getValue().getScheduledFor());
        }

        @Test
        void shouldReturnNullWhenNotificationsDisabled() {
            properties.setEnabled(false);
            notificationService = new NotificationService(
                    notificationRepository, studentRepository, dispatcher, properties,
                    new NotificationMessageFactory()
            );

            NotificationCreateRequest request = NotificationCreateRequest.simple(
                    UUID.randomUUID(),
                    NotificationType.CLASS_SCHEDULED,
                    Map.of()
            );

            NotificationResponse response = notificationService.create(request);

            assertNull(response);
            verify(notificationRepository, never()).save(any());
        }

        @Test
        void shouldThrowWhenStudentNotFound() {
            UUID studentId = UUID.randomUUID();
            when(studentRepository.findById(studentId)).thenReturn(Optional.empty());

            NotificationCreateRequest request = NotificationCreateRequest.simple(
                    studentId,
                    NotificationType.CLASS_SCHEDULED,
                    Map.of()
            );

            assertThrows(NotFoundException.class, () -> notificationService.create(request));
        }
    }

    @Nested
    class OptInBehavior {

        @Test
        void shouldSkipNotificationWhenStudentNotOptedIn() {
            Student student = createEligibleStudent();
            student.setNotificationOptIn(false);
            UUID studentId = student.getId();

            when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
            when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> {
                Notification n = i.getArgument(0);
                n.setId(UUID.randomUUID());
                return n;
            });

            NotificationCreateRequest request = NotificationCreateRequest.simple(
                    studentId,
                    NotificationType.CLASS_SCHEDULED,
                    Map.of()
            );

            NotificationResponse response = notificationService.create(request);

            assertNotNull(response);
            assertEquals(NotificationStatus.SKIPPED, response.status());

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            assertEquals(NotificationStatus.SKIPPED, captor.getValue().getStatus());
            assertNotNull(captor.getValue().getErrorMessage());
            assertTrue(captor.getValue().getErrorMessage().contains("opted in"));
        }

        @Test
        void shouldSkipNotificationWhenStudentInactive() {
            Student student = createEligibleStudent();
            student.setActive(false);
            UUID studentId = student.getId();

            when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
            when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> {
                Notification n = i.getArgument(0);
                n.setId(UUID.randomUUID());
                return n;
            });

            NotificationCreateRequest request = NotificationCreateRequest.simple(
                    studentId,
                    NotificationType.CLASS_CANCELLED,
                    Map.of()
            );

            NotificationResponse response = notificationService.create(request);

            assertEquals(NotificationStatus.SKIPPED, response.status());

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            assertTrue(captor.getValue().getErrorMessage().contains("inactive"));
        }

        @Test
        void shouldSkipNotificationWhenChannelIsNone() {
            Student student = createEligibleStudent();
            student.setPreferredNotificationChannel(NotificationChannel.NONE);
            UUID studentId = student.getId();

            when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
            when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> {
                Notification n = i.getArgument(0);
                n.setId(UUID.randomUUID());
                return n;
            });

            NotificationCreateRequest request = NotificationCreateRequest.simple(
                    studentId,
                    NotificationType.CLASS_SCHEDULED,
                    Map.of()
            );

            NotificationResponse response = notificationService.create(request);

            assertEquals(NotificationStatus.SKIPPED, response.status());
        }

        @Test
        void shouldSkipNotificationWhenMissingContactInfoForChannel() {
            Student student = createEligibleStudent();
            student.setPreferredNotificationChannel(NotificationChannel.SMS);
            student.setPhoneE164(null);
            UUID studentId = student.getId();

            when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
            // Note: hasProvider check comes after canReceiveNotificationsOn, so no stub needed
            when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> {
                Notification n = i.getArgument(0);
                n.setId(UUID.randomUUID());
                return n;
            });

            NotificationCreateRequest request = NotificationCreateRequest.simple(
                    studentId,
                    NotificationType.CLASS_REMINDER,
                    Map.of()
            );

            NotificationResponse response = notificationService.create(request);

            assertEquals(NotificationStatus.SKIPPED, response.status());
            assertTrue(response.errorMessage().contains("cannot receive"));
        }

        @Test
        void shouldSkipNotificationWhenNoProviderAvailable() {
            Student student = createEligibleStudent();
            UUID studentId = student.getId();

            when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
            when(dispatcher.hasProvider(NotificationChannel.EMAIL)).thenReturn(false);
            when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> {
                Notification n = i.getArgument(0);
                n.setId(UUID.randomUUID());
                return n;
            });

            NotificationCreateRequest request = NotificationCreateRequest.simple(
                    studentId,
                    NotificationType.CLASS_SCHEDULED,
                    Map.of()
            );

            NotificationResponse response = notificationService.create(request);

            assertEquals(NotificationStatus.SKIPPED, response.status());
            assertTrue(response.errorMessage().contains("No provider"));
        }

        @Test
        void shouldAllowWhatsAppWithPhoneE164AsFallback() {
            Student student = createEligibleStudent();
            student.setPreferredNotificationChannel(NotificationChannel.WHATSAPP);
            student.setWhatsappNumberE164(null);
            student.setPhoneE164("+12025551234");
            UUID studentId = student.getId();

            when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
            when(dispatcher.hasProvider(NotificationChannel.WHATSAPP)).thenReturn(true);
            when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> {
                Notification n = i.getArgument(0);
                n.setId(UUID.randomUUID());
                return n;
            });

            NotificationCreateRequest request = NotificationCreateRequest.simple(
                    studentId,
                    NotificationType.CLASS_SCHEDULED,
                    Map.of()
            );

            NotificationResponse response = notificationService.create(request);

            assertEquals(NotificationStatus.PENDING, response.status());
            assertEquals(NotificationChannel.WHATSAPP, response.channel());
        }

        @Test
        void shouldUseExplicitChannelOverPreferred() {
            Student student = createEligibleStudent();
            student.setPreferredNotificationChannel(NotificationChannel.EMAIL);
            student.setPhoneE164("+12025551234");
            UUID studentId = student.getId();

            when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
            when(dispatcher.hasProvider(NotificationChannel.SMS)).thenReturn(true);
            when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> {
                Notification n = i.getArgument(0);
                n.setId(UUID.randomUUID());
                return n;
            });

            NotificationCreateRequest request = new NotificationCreateRequest(
                    studentId,
                    NotificationChannel.SMS,
                    NotificationType.CLASS_REMINDER,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            NotificationResponse response = notificationService.create(request);

            assertEquals(NotificationStatus.PENDING, response.status());
            assertEquals(NotificationChannel.SMS, response.channel());
        }
    }

    @Nested
    class ImmediateDispatch {

        @BeforeEach
        void enableImmediateDispatch() {
            properties.getDispatcher().setMode(NotificationProperties.DispatchMode.STORE_AND_DISPATCH);
        }

        @Test
        void shouldDispatchImmediatelyWhenConfigured() {
            Student student = createEligibleStudent();
            UUID studentId = student.getId();

            when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
            when(dispatcher.hasProvider(NotificationChannel.EMAIL)).thenReturn(true);
            when(dispatcher.dispatch(any())).thenReturn(SendResult.sent("msg-123"));
            when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> {
                Notification n = i.getArgument(0);
                if (n.getId() == null) {
                    n.setId(UUID.randomUUID());
                }
                return n;
            });

            NotificationCreateRequest request = NotificationCreateRequest.simple(
                    studentId,
                    NotificationType.CLASS_SCHEDULED,
                    Map.of()
            );

            NotificationResponse response = notificationService.create(request);

            verify(dispatcher).dispatch(any());
            assertEquals(NotificationStatus.SENT, response.status());
        }

        @Test
        void shouldNotDispatchScheduledNotificationsImmediately() {
            Student student = createEligibleStudent();
            UUID studentId = student.getId();
            OffsetDateTime future = OffsetDateTime.now().plusHours(1);

            when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
            when(dispatcher.hasProvider(NotificationChannel.EMAIL)).thenReturn(true);
            when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> {
                Notification n = i.getArgument(0);
                n.setId(UUID.randomUUID());
                return n;
            });

            NotificationCreateRequest request = new NotificationCreateRequest(
                    studentId,
                    NotificationChannel.EMAIL,
                    NotificationType.CLASS_REMINDER,
                    null,
                    null,
                    null,
                    null,
                    future,
                    null
            );

            NotificationResponse response = notificationService.create(request);

            verify(dispatcher, never()).dispatch(any());
            assertEquals(NotificationStatus.PENDING, response.status());
        }
    }

    @Nested
    class CreateForEvent {

        @Test
        void shouldCreateNotificationForEvent() {
            Student student = createEligibleStudent();
            UUID studentId = student.getId();

            when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
            when(dispatcher.hasProvider(NotificationChannel.EMAIL)).thenReturn(true);
            when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> {
                Notification n = i.getArgument(0);
                n.setId(UUID.randomUUID());
                return n;
            });

            Map<String, String> variables = new HashMap<>();
            variables.put("slotTime", "Monday 10:00 AM");
            variables.put("teacherName", "Ms. Smith");

            NotificationResponse response = notificationService.createForEvent(
                    studentId,
                    NotificationType.CLASS_SCHEDULED,
                    variables
            );

            assertNotNull(response);
            assertEquals(NotificationType.CLASS_SCHEDULED, response.type());

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            assertEquals("Monday 10:00 AM", captor.getValue().getVariables().get("slotTime"));
        }
    }
}
