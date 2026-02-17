package com.bellgado.calendar.notification;

import com.bellgado.calendar.domain.enums.NotificationChannel;
import com.bellgado.calendar.notification.provider.NotificationMessage;
import com.bellgado.calendar.notification.provider.NotificationProvider;
import com.bellgado.calendar.notification.provider.SendResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Routes notification messages to the appropriate provider based on channel.
 *
 * <p>Provider selection logic:
 * <ol>
 *   <li>Filters providers that support the requested channel</li>
 *   <li>Filters providers that are currently available</li>
 *   <li>Selects the provider with the highest priority</li>
 * </ol>
 *
 * <p>If no provider is available for a channel, the message is skipped.
 */
@Slf4j
@Component
public class NotificationDispatcher {

    private final List<NotificationProvider> providers;
    private final Map<NotificationChannel, List<NotificationProvider>> providersByChannel;

    public NotificationDispatcher(List<NotificationProvider> providers) {
        this.providers = providers != null ? providers : List.of();
        this.providersByChannel = buildProviderMap();

        log.info("NotificationDispatcher initialized with {} providers: {}",
                this.providers.size(),
                this.providers.stream().map(NotificationProvider::getName).collect(Collectors.joining(", ")));

        // Log provider mapping per channel
        for (NotificationChannel channel : NotificationChannel.values()) {
            if (channel.isDeliverable()) {
                List<NotificationProvider> channelProviders = providersByChannel.getOrDefault(channel, List.of());
                log.info("  {} -> {} provider(s): {}", channel,
                        channelProviders.size(),
                        channelProviders.stream().map(NotificationProvider::getName).collect(Collectors.joining(", ")));
            }
        }
    }

    /**
     * Dispatches a notification message to the appropriate provider.
     *
     * @param message the message to dispatch
     * @return the result of the send operation
     */
    public SendResult dispatch(NotificationMessage message) {
        NotificationChannel channel = message.channel();

        if (!channel.isDeliverable()) {
            log.debug("Channel {} is not deliverable, skipping notification {}", channel, message.notificationId());
            return SendResult.skipped("Channel " + channel + " is not deliverable");
        }

        NotificationProvider provider = selectProvider(channel);

        if (provider == null) {
            log.warn("No provider available for channel {}, skipping notification {}", channel, message.notificationId());
            return SendResult.skipped("No provider available for channel " + channel);
        }

        log.debug("Dispatching notification {} to provider {} for channel {}",
                message.notificationId(), provider.getName(), channel);

        try {
            SendResult result = provider.send(message);

            if (result.success()) {
                log.info("Notification {} sent successfully via {} (messageId: {})",
                        message.notificationId(), provider.getName(), result.providerMessageId());
            } else {
                log.warn("Notification {} failed via {}: {} - {} (retryable: {})",
                        message.notificationId(), provider.getName(),
                        result.errorCode(), result.errorMessage(), result.retryable());
            }

            return result;
        } catch (Exception e) {
            log.error("Unexpected error sending notification {} via {}: {}",
                    message.notificationId(), provider.getName(), e.getMessage(), e);
            return SendResult.failedRetryable("PROVIDER_ERROR", "Provider error: " + e.getMessage());
        }
    }

    /**
     * Checks if a provider is available for the given channel.
     */
    public boolean hasProvider(NotificationChannel channel) {
        return selectProvider(channel) != null;
    }

    /**
     * Returns all registered providers.
     */
    public List<NotificationProvider> getProviders() {
        return Collections.unmodifiableList(providers);
    }

    /**
     * Returns providers for a specific channel.
     */
    public List<NotificationProvider> getProvidersForChannel(NotificationChannel channel) {
        return Collections.unmodifiableList(providersByChannel.getOrDefault(channel, List.of()));
    }

    private NotificationProvider selectProvider(NotificationChannel channel) {
        List<NotificationProvider> channelProviders = providersByChannel.getOrDefault(channel, List.of());

        return channelProviders.stream()
                .filter(NotificationProvider::isAvailable)
                .max(Comparator.comparingInt(NotificationProvider::getPriority))
                .orElse(null);
    }

    private Map<NotificationChannel, List<NotificationProvider>> buildProviderMap() {
        Map<NotificationChannel, List<NotificationProvider>> map = new EnumMap<>(NotificationChannel.class);

        for (NotificationChannel channel : NotificationChannel.values()) {
            if (channel.isDeliverable()) {
                List<NotificationProvider> channelProviders = providers.stream()
                        .filter(p -> p.supports(channel))
                        .sorted(Comparator.comparingInt(NotificationProvider::getPriority).reversed())
                        .toList();
                map.put(channel, channelProviders);
            }
        }

        return map;
    }
}
