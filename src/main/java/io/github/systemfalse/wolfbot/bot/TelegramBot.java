/*
 * Copyright (c) 2025 SystemFalse
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.github.systemfalse.wolfbot.bot;

import io.github.systemfalse.wolfbot.bot.handlers.CallbackHandler;
import io.github.systemfalse.wolfbot.bot.handlers.MessageHandler;
import io.github.systemfalse.wolfbot.bot.handlers.PhotoHandler;
import io.github.systemfalse.wolfbot.config.BotConfig;
import io.github.systemfalse.wolfbot.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramBot extends TelegramLongPollingBot {

    private final BotConfig botConfig;
    private final UserService userService;
    private final MessageHandler messageHandler;
    private final PhotoHandler photoHandler;
    private final CallbackHandler callbackHandler;

    public void init() {
        messageHandler.initBot(this);
        photoHandler.initBot(this);
        callbackHandler.initBot(this);
    }

    @Override
    public String getBotToken() {
        return botConfig.getToken();
    }

    @Override
    public String getBotUsername() {
        return botConfig.getUsername();
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            // Обработка сообщений с текстом
            if (update.hasMessage() && update.getMessage().hasText()) {
                messageHandler.handleTextMessage(update.getMessage());
            }
            // Обработка фотографий
            else if (update.hasMessage() && update.getMessage().hasPhoto()) {
                photoHandler.handlePhoto(update.getMessage());
            }
            // Обработка callback запросов (inline кнопки)
            else if (update.hasCallbackQuery()) {
                callbackHandler.handleCallback(update.getCallbackQuery());
            }

            // Обновляем время последней активности пользователя
            Long userId = getUserIdFromUpdate(update);
            if (userId != null) {
                userService.updateUserActivity(userId);
            }

        } catch (Exception e) {
            log.error("Ошибка при обработке обновления: ", e);
        }
    }

    /**
     * Отправка текстового сообщения
     */
    public void sendTextMessage(Long chatId, String text) {
        try {
            SendMessage message = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .parseMode("HTML")
                    .build();
            execute(message);
            log.debug("Отправлено сообщение пользователю {}: {}", chatId, text);
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке сообщения пользователю {}: ", chatId, e);
        }
    }

    /**
     * Отправка фотографии
     */
    public void sendPhoto(Long chatId, byte[] photoData, String caption) {
        try {
            SendPhoto sendPhoto = SendPhoto.builder()
                    .chatId(chatId.toString())
                    .photo(new org.telegram.telegrambots.meta.api.objects.InputFile(
                            new java.io.ByteArrayInputStream(photoData), "wolf.jpg"))
                    .caption(caption)
                    .parseMode("HTML")
                    .build();
            execute(sendPhoto);
            log.debug("Отправлена фотография пользователю {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке фотографии пользователю {}: ", chatId, e);
        }
    }

    /**
     * Получить ID пользователя из обновления
     */
    private Long getUserIdFromUpdate(Update update) {
        if (update.hasMessage()) {
            return update.getMessage().getFrom().getId();
        } else if (update.hasCallbackQuery()) {
            return update.getCallbackQuery().getFrom().getId();
        }
        return null;
    }
}
