package com.bellgado.calendar.agent.telegram;

import com.bellgado.calendar.agent.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.telegram.telegrambots.webhook.starter.SpringTelegramWebhookBot;

@Slf4j
public class CalendarWebhookBot {

    private final SpringTelegramWebhookBot webhookBot;
    private final TelegramUpdateHandler updateHandler;
    private final TelegramClient telegramClient;

    public CalendarWebhookBot(AgentProperties agentProperties,
                               TelegramUpdateHandler updateHandler,
                               TelegramClient telegramClient) {
        this.updateHandler = updateHandler;
        this.telegramClient = telegramClient;
        this.webhookBot = SpringTelegramWebhookBot.builder()
                .botPath("/api/telegram/webhook")
                .updateHandler(this::handleUpdate)
                .setWebhook(() -> log.info("Webhook set for bot: {}", agentProperties.getTelegram().getBotUsername()))
                .deleteWebhook(() -> log.info("Webhook deleted for bot"))
                .build();
    }

    private BotApiMethod<?> handleUpdate(Update update) {
        updateHandler.handleUpdate(update, telegramClient);
        return null;
    }

    public SpringTelegramWebhookBot getWebhookBot() {
        return webhookBot;
    }
}
