package notification_service.service;


import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import notification_service.config.AuthServiceClient;
import notification_service.model.UserTelegram;
import notification_service.repository.UserTelegramRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import core.core.exception.*;



import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramServiceTest {

    @Mock
    private TelegramBot telegramBot;
    @Mock
    private UserTelegramRepository userTelegramRepository;
    @Mock
    private AuthServiceClient authServiceClient;

    @InjectMocks
    private TelegramService telegramService;

    private static final Long CHAT_ID = 111111L;


    @Test
    @DisplayName("/start sends welcome message")
    void handleMessage_start_sendsWelcome() {
        telegramService.handleMessage(CHAT_ID, "/start");

        verify(telegramBot).execute(any(SendMessage.class));
        verify(authServiceClient, never()).verifyByCode(any());
    }

    @Test
    @DisplayName("6 digit code calls verifyUser")
    void handleMessage_6digitCode_callsVerify() {
        when(authServiceClient.verifyByCode("847291")).thenReturn(1L);
        when(userTelegramRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        telegramService.handleMessage(CHAT_ID, "847291");

        verify(authServiceClient).verifyByCode("847291");
    }

    @Test
    @DisplayName("Invalid text sends error message")
    void handleMessage_invalidText_sendsError() {
        telegramService.handleMessage(CHAT_ID, "hello");

        verify(telegramBot).execute(any());
        verify(authServiceClient, never()).verifyByCode(any());
    }
    

    @Test
    @DisplayName("verifyUser: Success saves UserTelegram and sends success message")
    void verifyUser_success_savesAndSendsMessage() {
        when(authServiceClient.verifyByCode("847291")).thenReturn(1L);
        when(userTelegramRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        telegramService.handleMessage(CHAT_ID, "847291");

        ArgumentCaptor<UserTelegram> captor = ArgumentCaptor.forClass(UserTelegram.class);
        verify(userTelegramRepository).save(captor.capture());

        assertEquals(1L, captor.getValue().getUserId());
        assertEquals(CHAT_ID, captor.getValue().getChatId());
        assertNotNull(captor.getValue().getCreatedAt());
    }

    @Test
    @DisplayName("verifyUser: Auth fails sends error message, no save")
    void verifyUser_authFails_sendsErrorNoSave() {
        when(authServiceClient.verifyByCode("000000"))
                .thenThrow(new RuntimeException("Invalid code"));

        telegramService.handleMessage(CHAT_ID, "000000");

        verify(userTelegramRepository, never()).save(any());
        verify(telegramBot).execute(any());
    }

    @Test
    @DisplayName("sendMessage: Success executes bot command")
    void sendMessage_success() {
        telegramService.sendMessage(CHAT_ID, "Hello!");

        verify(telegramBot).execute(any(SendMessage.class));
    }

    @Test
    @DisplayName("sendMessage: Bot throws logs error, does not propagate")
    void sendMessage_botThrows_doesNotPropagate() {
        when(telegramBot.execute(any(SendMessage.class)))
                .thenThrow(new RuntimeException("Bot error"));

        assertDoesNotThrow(() -> telegramService.sendMessage(CHAT_ID, "Hello!"));
    }
}