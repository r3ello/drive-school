package com.bellgado.calendar.notification;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for the notification system.
 *
 * <h2>Example Configuration</h2>
 * <pre>
 * notifications:
 *   enabled: true
 *   dispatcher:
 *     mode: STORE_AND_DISPATCH
 *   scheduler:
 *     enabled: false
 *     batch-size: 10
 *     poll-interval: PT30S
 *   providers:
 *     email: NOOP
 *     sms: NOOP
 *     whatsapp: NOOP
 *   defaults:
 *     max-attempts: 3
 *     expiry: PT24H
 * </pre>
 */
@ConfigurationProperties(prefix = "notifications")
@Validated
@Getter
@Setter
public class NotificationProperties {

    /**
     * Master switch for the notification system.
     * When false, no notifications are created or sent.
     */
    private boolean enabled = false;

    /**
     * Dispatcher configuration.
     */
    private DispatcherConfig dispatcher = new DispatcherConfig();

    /**
     * Scheduler configuration for async processing.
     */
    private SchedulerConfig scheduler = new SchedulerConfig();

    /**
     * Provider configuration per channel.
     */
    private Map<String, String> providers = new HashMap<>();

    /**
     * Default settings for notifications.
     */
    private DefaultsConfig defaults = new DefaultsConfig();

    // ============================================================================
    // NESTED CONFIG CLASSES
    // ============================================================================

    @Getter
    @Setter
    public static class DispatcherConfig {
        /**
         * Dispatcher mode:
         * - STORE_ONLY: Only store notifications in outbox (for later async processing)
         * - STORE_AND_DISPATCH: Store and immediately attempt to send
         */
        private DispatchMode mode = DispatchMode.STORE_ONLY;
    }

    @Getter
    @Setter
    public static class SchedulerConfig {
        /**
         * Enable/disable the background notification processor.
         */
        private boolean enabled = false;

        /**
         * Number of notifications to process per batch.
         */
        private int batchSize = 10;

        /**
         * Interval between polling for new notifications.
         */
        private Duration pollInterval = Duration.ofSeconds(30);

        /**
         * Lock timeout for notification processing.
         */
        private Duration lockTimeout = Duration.ofMinutes(5);
    }

    @Getter
    @Setter
    public static class DefaultsConfig {
        /**
         * Default maximum retry attempts.
         */
        private int maxAttempts = 3;

        /**
         * Default notification expiry duration.
         */
        private Duration expiry = Duration.ofHours(24);

        /**
         * Default priority for notifications.
         */
        private int priority = 0;
    }

    public enum DispatchMode {
        /**
         * Only store notifications in outbox for later processing.
         */
        STORE_ONLY,

        /**
         * Store and immediately attempt to dispatch.
         */
        STORE_AND_DISPATCH
    }

    /**
     * Checks if immediate dispatch is enabled.
     */
    public boolean isImmediateDispatch() {
        return enabled && dispatcher.getMode() == DispatchMode.STORE_AND_DISPATCH;
    }
}
