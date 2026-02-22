package com.bellgado.calendar.agent;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "agent")
@Validated
@Getter
@Setter
public class AgentProperties {

    private TelegramConfig telegram = new TelegramConfig();
    private String systemPrompt;

    @Getter
    @Setter
    public static class TelegramConfig {
        private String botToken;
        private String botUsername;
        private String webhookUrl;
        private String mode = "long-polling";
        private List<Long> allowedChatIds = new ArrayList<>();
    }
}
