package notification_service.service;


import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import notification_service.config.AuthServiceClient;
import notification_service.model.UserTelegram;
import notification_service.repository.UserTelegramRepository;

import org.springframework.stereotype.Service;

import java.time.Instant;



@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramService {

    private final TelegramBot telegramBot;
    private final UserTelegramRepository userTelegramRepository;
    private final AuthServiceClient authServiceClient;

    @PostConstruct
    public void init() {
        telegramBot.setUpdatesListener(updates -> {
            for (Update update : updates) {
                if (update.message() != null && update.message().text() != null) {
                    String text = update.message().text().trim();
                    Long chatId = update.message().chat().id();
                    handleMessage(chatId, text);
                }
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
        log.info("Telegram bot started");
    }
     void handleMessage(Long chatId, String text) {
        if ("/start".equalsIgnoreCase(text)) {
            sendMessage(chatId,
                    "Welcome to Finance App!\n" +
                            "To receive transaction notifications, send your userId.\n" +
                            "Example: 42");
            return;
        }

        if (text.matches("\\d{6}")) {
            verifyUser(chatId, text);
            return;
        }

        sendMessage(chatId, " Please send your 6-digit verification code.");
    }
     void verifyUser(Long chatId, String code) {
        try {
            Long userId = authServiceClient.verifyByCode(code);
            userTelegramRepository.save(UserTelegram.builder()
                    .userId(userId)
                    .chatId(chatId)
                    .createdAt(Instant.now())
                    .build());
            sendMessage(chatId, "Account verified! You can now login to Finance App.");
            log.info("User verified via Telegram chatId {}", chatId);
        } catch (Exception e) {
            log.error("Verification failed for chatId {}: {}", chatId, e.getMessage());
            sendMessage(chatId, "Invalid or expired code. Please register again.");
        }
    }


    public void sendMessage(Long chatId, String text) {
        try {
            telegramBot.execute(new SendMessage(chatId, text));
            log.info("Telegram sent to chatId {}", chatId);
        } catch (Exception e) {
            log.error("Failed to send Telegram to {}: {}", chatId, e.getMessage());
        }
    }
}