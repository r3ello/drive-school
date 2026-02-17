package com.bellgado.calendar.notification;

import com.bellgado.calendar.domain.enums.NotificationChannel;
import com.bellgado.calendar.domain.enums.NotificationType;
import com.bellgado.calendar.notification.provider.NotificationMessage;
import com.bellgado.calendar.notification.provider.NotificationProvider;
import com.bellgado.calendar.notification.provider.SendResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NotificationDispatcherTest {

    private NotificationMessage createMessage(NotificationChannel channel) {
        return NotificationMessage.builder()
                .notificationId(UUID.randomUUID())
                .studentId(UUID.randomUUID())
                .channel(channel)
                .type(NotificationType.CLASS_SCHEDULED)
                .recipient("test@example.com")
                .recipientName("Test User")
                .templateKey("CLASS_SCHEDULED")
                .variables(Map.of())
                .locale("en")
                .build();
    }

    @Nested
    class ProviderRouting {

        @Test
        void shouldRouteToProviderThatSupportsChannel() {
            TestProvider emailProvider = new TestProvider("EMAIL_PROVIDER", NotificationChannel.EMAIL);
            TestProvider smsProvider = new TestProvider("SMS_PROVIDER", NotificationChannel.SMS);

            NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(emailProvider, smsProvider));

            NotificationMessage emailMessage = createMessage(NotificationChannel.EMAIL);
            SendResult result = dispatcher.dispatch(emailMessage);

            assertTrue(result.success());
            assertEquals(SendResult.Status.SENT, result.status());
            assertTrue(emailProvider.wasCalled());
            assertFalse(smsProvider.wasCalled());
        }

        @Test
        void shouldRouteToCorrectProviderForSms() {
            TestProvider emailProvider = new TestProvider("EMAIL_PROVIDER", NotificationChannel.EMAIL);
            TestProvider smsProvider = new TestProvider("SMS_PROVIDER", NotificationChannel.SMS);

            NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(emailProvider, smsProvider));

            NotificationMessage smsMessage = createMessage(NotificationChannel.SMS);
            SendResult result = dispatcher.dispatch(smsMessage);

            assertTrue(result.success());
            assertFalse(emailProvider.wasCalled());
            assertTrue(smsProvider.wasCalled());
        }

        @Test
        void shouldSkipWhenNoProviderAvailable() {
            TestProvider emailProvider = new TestProvider("EMAIL_PROVIDER", NotificationChannel.EMAIL);
            NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(emailProvider));

            NotificationMessage smsMessage = createMessage(NotificationChannel.SMS);
            SendResult result = dispatcher.dispatch(smsMessage);

            assertTrue(result.success());
            assertEquals(SendResult.Status.SKIPPED, result.status());
            assertTrue(result.errorMessage().contains("No provider"));
        }

        @Test
        void shouldSkipWhenChannelIsNone() {
            TestProvider provider = new TestProvider("TEST", NotificationChannel.EMAIL);
            NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(provider));

            NotificationMessage message = createMessage(NotificationChannel.NONE);
            SendResult result = dispatcher.dispatch(message);

            assertTrue(result.success());
            assertEquals(SendResult.Status.SKIPPED, result.status());
            assertTrue(result.errorMessage().contains("not deliverable"));
            assertFalse(provider.wasCalled());
        }
    }

    @Nested
    class ProviderPriority {

        @Test
        void shouldSelectHigherPriorityProvider() {
            TestProvider lowPriority = new TestProvider("LOW", NotificationChannel.EMAIL, 0);
            TestProvider highPriority = new TestProvider("HIGH", NotificationChannel.EMAIL, 100);

            NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(lowPriority, highPriority));

            NotificationMessage message = createMessage(NotificationChannel.EMAIL);
            dispatcher.dispatch(message);

            assertTrue(highPriority.wasCalled());
            assertFalse(lowPriority.wasCalled());
        }

        @Test
        void shouldFallbackToLowerPriorityWhenHigherUnavailable() {
            TestProvider unavailable = new TestProvider("UNAVAILABLE", NotificationChannel.EMAIL, 100, false);
            TestProvider available = new TestProvider("AVAILABLE", NotificationChannel.EMAIL, 0, true);

            NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(unavailable, available));

            NotificationMessage message = createMessage(NotificationChannel.EMAIL);
            SendResult result = dispatcher.dispatch(message);

            assertTrue(result.success());
            assertFalse(unavailable.wasCalled());
            assertTrue(available.wasCalled());
        }

        @Test
        void shouldSkipWhenAllProvidersUnavailable() {
            TestProvider p1 = new TestProvider("P1", NotificationChannel.EMAIL, 100, false);
            TestProvider p2 = new TestProvider("P2", NotificationChannel.EMAIL, 50, false);

            NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(p1, p2));

            NotificationMessage message = createMessage(NotificationChannel.EMAIL);
            SendResult result = dispatcher.dispatch(message);

            assertEquals(SendResult.Status.SKIPPED, result.status());
        }
    }

    @Nested
    class MultiChannelSupport {

        @Test
        void shouldRouteSameProviderForMultipleChannels() {
            TestMultiChannelProvider multiProvider = new TestMultiChannelProvider(
                    "MULTI",
                    List.of(NotificationChannel.EMAIL, NotificationChannel.SMS, NotificationChannel.WHATSAPP)
            );

            NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(multiProvider));

            dispatcher.dispatch(createMessage(NotificationChannel.EMAIL));
            assertEquals(1, multiProvider.getCallCount());

            dispatcher.dispatch(createMessage(NotificationChannel.SMS));
            assertEquals(2, multiProvider.getCallCount());

            dispatcher.dispatch(createMessage(NotificationChannel.WHATSAPP));
            assertEquals(3, multiProvider.getCallCount());
        }
    }

    @Nested
    class HasProvider {

        @Test
        void shouldReturnTrueWhenProviderExists() {
            TestProvider provider = new TestProvider("EMAIL", NotificationChannel.EMAIL);
            NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(provider));

            assertTrue(dispatcher.hasProvider(NotificationChannel.EMAIL));
        }

        @Test
        void shouldReturnFalseWhenNoProviderExists() {
            TestProvider provider = new TestProvider("EMAIL", NotificationChannel.EMAIL);
            NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(provider));

            assertFalse(dispatcher.hasProvider(NotificationChannel.SMS));
        }

        @Test
        void shouldReturnFalseWhenProviderUnavailable() {
            TestProvider unavailable = new TestProvider("UNAVAILABLE", NotificationChannel.EMAIL, 0, false);
            NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(unavailable));

            assertFalse(dispatcher.hasProvider(NotificationChannel.EMAIL));
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void shouldReturnRetryableFailureOnProviderException() {
            TestProvider failingProvider = new TestProvider("FAILING", NotificationChannel.EMAIL) {
                @Override
                public SendResult send(NotificationMessage message) {
                    throw new RuntimeException("Connection failed");
                }
            };

            NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(failingProvider));

            NotificationMessage message = createMessage(NotificationChannel.EMAIL);
            SendResult result = dispatcher.dispatch(message);

            assertFalse(result.success());
            assertEquals(SendResult.Status.FAILED, result.status());
            assertTrue(result.retryable());
            assertEquals("PROVIDER_ERROR", result.errorCode());
            assertTrue(result.errorMessage().contains("Connection failed"));
        }
    }

    @Nested
    class GetProviders {

        @Test
        void shouldReturnAllProviders() {
            TestProvider p1 = new TestProvider("P1", NotificationChannel.EMAIL);
            TestProvider p2 = new TestProvider("P2", NotificationChannel.SMS);

            NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(p1, p2));

            assertEquals(2, dispatcher.getProviders().size());
        }

        @Test
        void shouldReturnProvidersForChannel() {
            TestProvider email1 = new TestProvider("EMAIL1", NotificationChannel.EMAIL, 0);
            TestProvider email2 = new TestProvider("EMAIL2", NotificationChannel.EMAIL, 10);
            TestProvider sms = new TestProvider("SMS", NotificationChannel.SMS);

            NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(email1, email2, sms));

            List<NotificationProvider> emailProviders = dispatcher.getProvidersForChannel(NotificationChannel.EMAIL);
            assertEquals(2, emailProviders.size());

            List<NotificationProvider> smsProviders = dispatcher.getProvidersForChannel(NotificationChannel.SMS);
            assertEquals(1, smsProviders.size());
        }

        @Test
        void shouldReturnEmptyListForChannelWithNoProviders() {
            TestProvider emailProvider = new TestProvider("EMAIL", NotificationChannel.EMAIL);
            NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(emailProvider));

            List<NotificationProvider> providers = dispatcher.getProvidersForChannel(NotificationChannel.WHATSAPP);
            assertTrue(providers.isEmpty());
        }
    }

    // Test helper implementations

    private static class TestProvider implements NotificationProvider {
        private final String name;
        private final NotificationChannel supportedChannel;
        private final int priority;
        private final boolean available;
        private boolean called = false;

        TestProvider(String name, NotificationChannel supportedChannel) {
            this(name, supportedChannel, 0, true);
        }

        TestProvider(String name, NotificationChannel supportedChannel, int priority) {
            this(name, supportedChannel, priority, true);
        }

        TestProvider(String name, NotificationChannel supportedChannel, int priority, boolean available) {
            this.name = name;
            this.supportedChannel = supportedChannel;
            this.priority = priority;
            this.available = available;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean supports(NotificationChannel channel) {
            return channel == supportedChannel;
        }

        @Override
        public SendResult send(NotificationMessage message) {
            called = true;
            return SendResult.sent("test-msg-id");
        }

        @Override
        public int getPriority() {
            return priority;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        boolean wasCalled() {
            return called;
        }
    }

    private static class TestMultiChannelProvider implements NotificationProvider {
        private final String name;
        private final List<NotificationChannel> channels;
        private int callCount = 0;

        TestMultiChannelProvider(String name, List<NotificationChannel> channels) {
            this.name = name;
            this.channels = channels;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean supports(NotificationChannel channel) {
            return channels.contains(channel);
        }

        @Override
        public SendResult send(NotificationMessage message) {
            callCount++;
            return SendResult.sent("multi-" + callCount);
        }

        int getCallCount() {
            return callCount;
        }
    }
}
