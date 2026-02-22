package com.bellgado.calendar.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AgentConfig {

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(30)
                .build();
    }

    @Bean
    public ChatClient chatClient(
            ChatClient.Builder builder,
            ChatMemory chatMemory,
            CalendarAgentTools calendarAgentTools,
            AgentProperties agentProperties) {

        return builder
                .defaultSystem(agentProperties.getSystemPrompt())
                .defaultTools(calendarAgentTools)
                .defaultAdvisors(
                        List.of(MessageChatMemoryAdvisor.builder(chatMemory).build(),
                                new SimpleLoggerAdvisor())
                )
                .build();
    }
}
