package com.bellgado.calendar.agent.telegram;

import com.bellgado.calendar.agent.AgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Configuration
@Profile("prod")
@Slf4j
public class WebhookBotConfig {

    @Bean
    public TelegramClient telegramClient(AgentProperties agentProperties) {
        return new OkHttpTelegramClient(agentProperties.getTelegram().getBotToken());
    }

    @Bean
    public ApplicationRunner registerWebhook(AgentProperties agentProperties, TelegramClient telegramClient) {
        return args -> {
            String webhookUrl = agentProperties.getTelegram().getWebhookUrl();
            telegramClient.execute(SetWebhook.builder().url(webhookUrl).build());
            log.info("Telegram webhook registered at: {}", webhookUrl);
        };
    }

    @RestController
    @Profile("prod")
    @RequiredArgsConstructor
    static class TelegramWebhookController {

        private final TelegramUpdateHandler updateHandler;
        private final TelegramClient telegramClient;

        @PostMapping("/api/telegram/webhook")
        public void onWebhookUpdate(@RequestBody Update update) {
            updateHandler.handleUpdate(update, telegramClient);
        }
    }
}
