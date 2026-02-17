package com.bellgado.calendar.domain.entity;

import com.bellgado.calendar.domain.enums.NotificationChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class StudentNotificationTest {

    private Student student;

    @BeforeEach
    void setUp() {
        student = new Student("John Doe", "+1 (202) 555-1234", "john@example.com", null);
        student.setActive(true);
        student.setNotificationOptIn(true);
        student.setPreferredNotificationChannel(NotificationChannel.EMAIL);
        student.setPhoneE164("+12025551234");
    }

    @Nested
    class CanReceiveNotifications {

        @Test
        void shouldReturnTrueWhenAllConditionsMet() {
            assertTrue(student.canReceiveNotifications());
        }

        @Test
        void shouldReturnFalseWhenInactive() {
            student.setActive(false);
            assertFalse(student.canReceiveNotifications());
        }

        @Test
        void shouldReturnFalseWhenNotOptedIn() {
            student.setNotificationOptIn(false);
            assertFalse(student.canReceiveNotifications());
        }

        @Test
        void shouldReturnFalseWhenChannelIsNone() {
            student.setPreferredNotificationChannel(NotificationChannel.NONE);
            assertFalse(student.canReceiveNotifications());
        }

        @Test
        void shouldReturnFalseWhenAllConditionsFail() {
            student.setActive(false);
            student.setNotificationOptIn(false);
            student.setPreferredNotificationChannel(NotificationChannel.NONE);
            assertFalse(student.canReceiveNotifications());
        }
    }

    @Nested
    class CanReceiveNotificationsOnChannel {

        @Nested
        class EmailChannel {

            @Test
            void shouldReturnTrueWhenEmailPresent() {
                student.setEmail("john@example.com");
                assertTrue(student.canReceiveNotificationsOn(NotificationChannel.EMAIL));
            }

            @Test
            void shouldReturnFalseWhenEmailNull() {
                student.setEmail(null);
                assertFalse(student.canReceiveNotificationsOn(NotificationChannel.EMAIL));
            }

            @Test
            void shouldReturnFalseWhenEmailEmpty() {
                student.setEmail("");
                assertFalse(student.canReceiveNotificationsOn(NotificationChannel.EMAIL));
            }

            @Test
            void shouldReturnFalseWhenEmailBlank() {
                student.setEmail("   ");
                assertFalse(student.canReceiveNotificationsOn(NotificationChannel.EMAIL));
            }
        }

        @Nested
        class SmsChannel {

            @Test
            void shouldReturnTrueWhenPhoneE164Present() {
                student.setPhoneE164("+12025551234");
                assertTrue(student.canReceiveNotificationsOn(NotificationChannel.SMS));
            }

            @Test
            void shouldReturnFalseWhenPhoneE164Null() {
                student.setPhoneE164(null);
                assertFalse(student.canReceiveNotificationsOn(NotificationChannel.SMS));
            }

            @Test
            void shouldReturnFalseWhenPhoneE164Empty() {
                student.setPhoneE164("");
                assertFalse(student.canReceiveNotificationsOn(NotificationChannel.SMS));
            }
        }

        @Nested
        class WhatsAppChannel {

            @Test
            void shouldReturnTrueWhenWhatsAppNumberPresent() {
                student.setWhatsappNumberE164("+12025559999");
                assertTrue(student.canReceiveNotificationsOn(NotificationChannel.WHATSAPP));
            }

            @Test
            void shouldReturnTrueWhenUsingPhoneAsFallback() {
                student.setWhatsappNumberE164(null);
                student.setPhoneE164("+12025551234");
                assertTrue(student.canReceiveNotificationsOn(NotificationChannel.WHATSAPP));
            }

            @Test
            void shouldReturnFalseWhenNoWhatsAppAndNoPhone() {
                student.setWhatsappNumberE164(null);
                student.setPhoneE164(null);
                assertFalse(student.canReceiveNotificationsOn(NotificationChannel.WHATSAPP));
            }
        }

        @Nested
        class NoneChannel {

            @Test
            void shouldAlwaysReturnFalseForNoneChannel() {
                assertFalse(student.canReceiveNotificationsOn(NotificationChannel.NONE));
            }
        }

        @Test
        void shouldReturnFalseWhenBaseConditionsNotMet() {
            student.setActive(false);
            assertFalse(student.canReceiveNotificationsOn(NotificationChannel.EMAIL));

            student.setActive(true);
            student.setNotificationOptIn(false);
            assertFalse(student.canReceiveNotificationsOn(NotificationChannel.EMAIL));
        }
    }

    @Nested
    class EffectiveWhatsappNumber {

        @Test
        void shouldReturnWhatsAppNumberWhenSet() {
            student.setWhatsappNumberE164("+12025559999");
            student.setPhoneE164("+12025551234");

            assertEquals("+12025559999", student.getEffectiveWhatsappNumber());
        }

        @Test
        void shouldFallbackToPhoneWhenWhatsAppNull() {
            student.setWhatsappNumberE164(null);
            student.setPhoneE164("+12025551234");

            assertEquals("+12025551234", student.getEffectiveWhatsappNumber());
        }

        @Test
        void shouldFallbackToPhoneWhenWhatsAppEmpty() {
            student.setWhatsappNumberE164("");
            student.setPhoneE164("+12025551234");

            assertEquals("+12025551234", student.getEffectiveWhatsappNumber());
        }

        @Test
        void shouldFallbackToPhoneWhenWhatsAppBlank() {
            student.setWhatsappNumberE164("   ");
            student.setPhoneE164("+12025551234");

            assertEquals("+12025551234", student.getEffectiveWhatsappNumber());
        }

        @Test
        void shouldReturnNullWhenBothNull() {
            student.setWhatsappNumberE164(null);
            student.setPhoneE164(null);

            assertNull(student.getEffectiveWhatsappNumber());
        }
    }

    @Nested
    class OptInBehavior {

        @Test
        void shouldSetOptInTimestampWhenOptingIn() {
            student = new Student("Test", null, null, null);
            assertNull(student.getNotificationOptInAt());

            student.setNotificationOptIn(true);

            assertTrue(student.isNotificationOptIn());
            assertNotNull(student.getNotificationOptInAt());
        }

        @Test
        void shouldNotOverwriteTimestampOnSubsequentOptIn() {
            student = new Student("Test", null, null, null);
            student.setNotificationOptIn(true);
            OffsetDateTime firstOptInTime = student.getNotificationOptInAt();

            student.setNotificationOptIn(true);

            assertEquals(firstOptInTime, student.getNotificationOptInAt());
        }

        @Test
        void shouldPreserveTimestampWhenOptingOut() {
            student.setNotificationOptIn(true);
            OffsetDateTime optInTime = student.getNotificationOptInAt();

            student.setNotificationOptIn(false);

            assertFalse(student.isNotificationOptIn());
            assertEquals(optInTime, student.getNotificationOptInAt());
        }
    }

    @Nested
    class DefaultValues {

        @Test
        void shouldHaveDefaultNotificationChannelNone() {
            Student newStudent = new Student("Test", null, null, null);
            assertEquals(NotificationChannel.NONE, newStudent.getPreferredNotificationChannel());
        }

        @Test
        void shouldHaveDefaultOptInFalse() {
            Student newStudent = new Student("Test", null, null, null);
            assertFalse(newStudent.isNotificationOptIn());
        }

        @Test
        void shouldHaveDefaultTimezoneUtc() {
            Student newStudent = new Student("Test", null, null, null);
            assertEquals("UTC", newStudent.getTimezone());
        }

        @Test
        void shouldHaveDefaultLocaleEnglish() {
            Student newStudent = new Student("Test", null, null, null);
            assertEquals("en", newStudent.getLocale());
        }

        @Test
        void shouldHaveNullQuietHoursByDefault() {
            Student newStudent = new Student("Test", null, null, null);
            assertNull(newStudent.getQuietHoursStart());
            assertNull(newStudent.getQuietHoursEnd());
        }
    }

    @Nested
    class BackwardCompatibility {

        @Test
        void shouldKeepOriginalPhoneFieldSeparate() {
            student.setPhone("+1 (202) 555-1234");
            student.setPhoneE164("+12025551234");

            assertEquals("+1 (202) 555-1234", student.getPhone());
            assertEquals("+12025551234", student.getPhoneE164());
        }
    }
}
