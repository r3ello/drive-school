package com.bellgado.calendar.agent.telegram;

import com.bellgado.calendar.agent.AgentProperties;
import com.bellgado.calendar.agent.AgentService;
import com.bellgado.calendar.agent.VoiceTranscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.Voice;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramUpdateHandlerTest {

    @Mock
    private AgentService agentService;
    @Mock
    private TelegramClient telegramClient;
    @Mock
    private VoiceTranscriptionService voiceTranscriptionService;

    private AgentProperties agentProperties;
    private TelegramUpdateHandler handler;

    @BeforeEach
    void setUp() {
        agentProperties = new AgentProperties();
        agentProperties.getTelegram().setAllowedChatIds(List.of(111L, 222L));
        handler = new TelegramUpdateHandler(agentService, agentProperties, voiceTranscriptionService);
    }

    @Test
    void unauthorizedChatId_sendsRejectionMessage() throws TelegramApiException {
        Update update = createTextUpdate(999L, "Hello");

        handler.handleUpdate(update, telegramClient);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertThat(captor.getValue().getText()).contains("not authorized");
        verify(agentService, never()).chat(any(), any());
    }

    @Test
    void authorizedChatId_forwardsToAgentService() throws TelegramApiException {
        Update update = createTextUpdate(111L, "Show me free slots");
        when(agentService.chat(eq("tg-111"), eq("Show me free slots")))
                .thenReturn("Here are the free slots...");

        handler.handleUpdate(update, telegramClient);

        verify(agentService).chat("tg-111", "Show me free slots");
    }

    @Test
    void startCommand_sendsWelcomeMessage() throws TelegramApiException {
        Update update = createTextUpdate(111L, "/start");

        handler.handleUpdate(update, telegramClient);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertThat(captor.getValue().getText()).contains("calendar scheduling assistant");
        verify(agentService, never()).chat(any(), any());
    }

    @Test
    void emptyAllowedList_allowsEveryone() throws TelegramApiException {
        agentProperties.getTelegram().setAllowedChatIds(List.of());
        handler = new TelegramUpdateHandler(agentService, agentProperties, voiceTranscriptionService);

        Update update = createTextUpdate(999L, "Hello");
        when(agentService.chat(any(), any())).thenReturn("Hi");

        handler.handleUpdate(update, telegramClient);

        verify(agentService).chat("tg-999", "Hello");
    }

    @Test
    void nonTextMessage_isIgnored() throws TelegramApiException {
        Update update = new Update();
        // No message set

        handler.handleUpdate(update, telegramClient);

        verify(telegramClient, never()).execute(any(SendMessage.class));
        verify(agentService, never()).chat(any(), any());
    }

    // --- Voice message tests ---

    @Test
    void voiceMessage_transcribesAndForwardsToAgent() throws TelegramApiException {
        Update update = createVoiceUpdate(111L, "file-id-123");
        File telegramFile = new File("file-id-123", "unique-id", null, "voice/file_123.ogg");
        when(telegramClient.execute(any(GetFile.class))).thenReturn(telegramFile);
        when(voiceTranscriptionService.transcribe("voice/file_123.ogg"))
                .thenReturn("Book a slot for tomorrow");
        when(agentService.chat(eq("tg-111"), eq("Book a slot for tomorrow")))
                .thenReturn("Slot booked!");

        handler.handleUpdate(update, telegramClient);

        verify(voiceTranscriptionService).transcribe("voice/file_123.ogg");
        verify(agentService).chat("tg-111", "Book a slot for tomorrow");
    }

    @Test
    void voiceMessage_transcriptionFails_sendsErrorMessage() throws TelegramApiException {
        Update update = createVoiceUpdate(111L, "file-id-123");
        File telegramFile = new File("file-id-123", "unique-id", null, "voice/file_123.ogg");
        when(telegramClient.execute(any(GetFile.class))).thenReturn(telegramFile);
        when(voiceTranscriptionService.transcribe("voice/file_123.ogg"))
                .thenThrow(new RuntimeException("Whisper API error"));

        handler.handleUpdate(update, telegramClient);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertThat(captor.getValue().getText()).contains("couldn't process your voice message");
        verify(agentService, never()).chat(any(), any());
    }

    @Test
    void voiceMessage_emptyTranscript_sendsCouldNotUnderstand() throws TelegramApiException {
        Update update = createVoiceUpdate(111L, "file-id-123");
        File telegramFile = new File("file-id-123", "unique-id", null, "voice/file_123.ogg");
        when(telegramClient.execute(any(GetFile.class))).thenReturn(telegramFile);
        when(voiceTranscriptionService.transcribe("voice/file_123.ogg")).thenReturn("   ");

        handler.handleUpdate(update, telegramClient);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertThat(captor.getValue().getText()).contains("couldn't understand");
        verify(agentService, never()).chat(any(), any());
    }

    @Test
    void voiceMessage_unauthorizedChatId_rejectedWithoutTranscribing() throws TelegramApiException {
        Update update = createVoiceUpdate(999L, "file-id-123");

        handler.handleUpdate(update, telegramClient);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertThat(captor.getValue().getText()).contains("not authorized");
        verify(voiceTranscriptionService, never()).transcribe(any());
        verify(agentService, never()).chat(any(), any());
    }

    // --- Helpers ---

    private Update createTextUpdate(long chatId, String text) {
        Chat chat = Chat.builder()
                .id(chatId)
                .type("private")
                .build();

        Message message = Message.builder()
                .chat(chat)
                .text(text)
                .build();

        Update update = new Update();
        update.setMessage(message);
        return update;
    }

    private Update createVoiceUpdate(long chatId, String fileId) {
        Chat chat = Chat.builder()
                .id(chatId)
                .type("private")
                .build();

        Voice voice = Voice.builder()
                .fileId(fileId)
                .fileUniqueId("unique-" + fileId)
                .duration(5)
                .build();

        Message message = Message.builder()
                .chat(chat)
                .voice(voice)
                .build();

        Update update = new Update();
        update.setMessage(message);
        return update;
    }
}
