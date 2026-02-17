package com.bellgado.calendar.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentService {

    private final ChatClient chatClient;
    private final AgentProperties agentProperties;

    public String chat(String conversationId, String userMessage) {
        log.debug("Agent chat [{}]: {}", conversationId, userMessage);

        String currentDate = LocalDate.now(ZoneId.of("Europe/Sofia")).toString();
        String systemPromptWithDate = agentProperties.getSystemPrompt()
                .replace("{current_date}", currentDate);

        try {
            String response = chatClient.prompt()
                    .system(systemPromptWithDate)
                    .user(userMessage)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();

            log.debug("Agent response [{}]: {}", conversationId, response);
            return response;
        } catch (Exception e) {
            log.error("AI processing error for conversation {}: {}", conversationId, e.getMessage(), e);
            return "I'm having trouble processing your request right now. Please try again in a moment.";
        }
    }
}
