package com.bellgado.calendar.agent.telegram;

import com.bellgado.calendar.agent.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Configuration
@Profile("dev")
@Slf4j
public class LongPollingBotConfig {

    @Bean
    public TelegramClient telegramClient(AgentProperties agentProperties) {
        return new OkHttpTelegramClient(agentProperties.getTelegram().getBotToken());
    }

    @Bean
    public CalendarLongPollingBot calendarLongPollingBot(
            AgentProperties agentProperties,
            TelegramUpdateHandler updateHandler,
            TelegramClient telegramClient) {
        log.info("Initializing Telegram bot in LONG-POLLING mode (dev)");
        return new CalendarLongPollingBot(agentProperties, updateHandler, telegramClient);
    }

    @Bean
    public TelegramBotsLongPollingApplication telegramBotsApplication() {
        return new TelegramBotsLongPollingApplication();
    }

    @Bean
    public ApplicationRunner registerLongPollingBot(
            TelegramBotsLongPollingApplication application,
            CalendarLongPollingBot bot) {
        return args -> {
            application.registerBot(bot.getBotToken(), bot.getUpdatesConsumer());
            log.info("Telegram long-polling bot registered and polling started");
        };
    }
}
