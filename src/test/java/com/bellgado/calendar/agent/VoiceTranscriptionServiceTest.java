package com.bellgado.calendar.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoiceTranscriptionServiceTest {

    @Mock
    private RestClient restClient;
    @Mock
    private RestClient.RequestHeadersUriSpec<?> getSpec;
    @Mock
    private RestClient.RequestHeadersSpec<?> getHeadersSpec;
    @Mock
    private RestClient.ResponseSpec getResponseSpec;
    @Mock
    private RestClient.RequestBodyUriSpec postSpec;
    @Mock
    private RestClient.RequestBodySpec postBodySpec;
    @Mock
    private RestClient.ResponseSpec postResponseSpec;

    private VoiceTranscriptionService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        RestClient.Builder builder = mock(RestClient.Builder.class);
        when(builder.build()).thenReturn(restClient);

        AgentProperties agentProperties = new AgentProperties();
        agentProperties.getTelegram().setBotToken("test-bot-token");

        service = new VoiceTranscriptionService(builder, agentProperties, "test-openai-key");
    }

    @Test
    @SuppressWarnings("unchecked")
    void transcribe_happyPath_returnsTranscript() {
        setupDownloadMock(new byte[]{1, 2, 3});
        setupWhisperMock(Map.of("text", "Book a slot for tomorrow"));

        String result = service.transcribe("voice/file_123.ogg");

        assertThat(result).isEqualTo("Book a slot for tomorrow");
    }

    @Test
    @SuppressWarnings("unchecked")
    void transcribe_downloadFails_propagatesException() {
        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) getSpec);
        doReturn(getHeadersSpec).when(getSpec).uri(anyString());
        when(getHeadersSpec.retrieve()).thenThrow(new RuntimeException("Download failed"));

        assertThatThrownBy(() -> service.transcribe("voice/file_123.ogg"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Download failed");
    }

    @Test
    @SuppressWarnings("unchecked")
    void transcribe_whisperFails_propagatesException() {
        setupDownloadMock(new byte[]{1, 2, 3});

        when(restClient.post()).thenReturn(postSpec);
        when(postSpec.uri(anyString())).thenReturn(postBodySpec);
        when(postBodySpec.header(anyString(), anyString())).thenReturn(postBodySpec);
        when(postBodySpec.contentType(any())).thenReturn(postBodySpec);
        when(postBodySpec.body(any(Object.class))).thenReturn(postBodySpec);
        when(postBodySpec.retrieve()).thenThrow(new RuntimeException("Whisper API error"));

        assertThatThrownBy(() -> service.transcribe("voice/file_123.ogg"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Whisper API error");
    }

    @SuppressWarnings("unchecked")
    private void setupDownloadMock(byte[] audioBytes) {
        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) getSpec);
        doReturn(getHeadersSpec).when(getSpec).uri(anyString());
        when(getHeadersSpec.retrieve()).thenReturn(getResponseSpec);
        when(getResponseSpec.body(byte[].class)).thenReturn(audioBytes);
    }

    @SuppressWarnings("unchecked")
    private void setupWhisperMock(Map<String, String> response) {
        when(restClient.post()).thenReturn(postSpec);
        when(postSpec.uri(anyString())).thenReturn(postBodySpec);
        when(postBodySpec.header(anyString(), anyString())).thenReturn(postBodySpec);
        when(postBodySpec.contentType(any())).thenReturn(postBodySpec);
        when(postBodySpec.body(any(Object.class))).thenReturn(postBodySpec);
        when(postBodySpec.retrieve()).thenReturn(postResponseSpec);
        when(postResponseSpec.body(Map.class)).thenReturn(response);
    }
}
