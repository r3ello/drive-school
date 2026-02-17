package com.bellgado.calendar.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock
    private ChatClient chatClient;
    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private AgentProperties agentProperties;
    private AgentService agentService;

    @BeforeEach
    void setUp() {
        agentProperties = new AgentProperties();
        agentProperties.setSystemPrompt("Test prompt. Today is {current_date}.");
        agentService = new AgentService(chatClient, agentProperties);
    }

    @SuppressWarnings("unchecked")
    private void setupMocks(String aiResponse) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.advisors(any(java.util.function.Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(aiResponse);
    }

    @Test
    void chat_returnsAiResponse() {
        setupMocks("Here are your slots");

        String result = agentService.chat("tg-123", "Show me slots");

        assertThat(result).isEqualTo("Here are your slots");
        verify(requestSpec).user("Show me slots");
    }

    @Test
    void chat_replacesCurrentDateInSystemPrompt() {
        setupMocks("ok");

        agentService.chat("tg-123", "test");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(captor.capture());
        String systemPrompt = captor.getValue();
        assertThat(systemPrompt).doesNotContain("{current_date}");
        assertThat(systemPrompt).contains("Test prompt");
    }

    @Test
    void chat_returnsFallbackOnException() {
        when(chatClient.prompt()).thenThrow(new RuntimeException("API down"));

        String result = agentService.chat("tg-123", "test");

        assertThat(result).contains("having trouble");
    }
}
