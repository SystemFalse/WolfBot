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

package io.github.systemfalse.wolfbot.bot.handlers;

import io.github.systemfalse.wolfbot.bot.TelegramBot;
import io.github.systemfalse.wolfbot.model.ImageStatus;
import io.github.systemfalse.wolfbot.service.ModerationService;
import io.github.systemfalse.wolfbot.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CallbackHandler {

    private TelegramBot telegramBot;
    private final ModerationService moderationService;
    private final UserService userService;

    public void initBot(TelegramBot telegramBot) {
        this.telegramBot = telegramBot;
    }

    /**
     * Обработка callback запроса
     */
    public void handleCallback(CallbackQuery callbackQuery) {
        String callbackData = callbackQuery.getData();
        Long userId = callbackQuery.getFrom().getId();
        String messageId = callbackQuery.getMessage().getMessageId().toString();
        Long chatId = callbackQuery.getMessage().getChatId();

        log.info("Получен callback от пользователя {}: {}", userId, callbackData);

        try {
            // Проверяем, является ли пользователь модератором
            if (!moderationService.isModerator(userId)) {
                answerCallbackQuery(callbackQuery.getId(),
                        "❌ У вас нет прав для выполнения этого действия.", true);
                return;
            }

            // Обрабатываем различные типы callback'ов
            if (callbackData.startsWith("moderate_")) {
                handleModerationCallback(callbackQuery, callbackData);
            } else if (callbackData.startsWith("schedule_")) {
                handleScheduleCallback(callbackQuery, callbackData);
            } else {
                log.warn("Неизвестный callback: {}", callbackData);
                answerCallbackQuery(callbackQuery.getId(),
                        "❌ Неизвестная команда.", true);
            }

        } catch (Exception e) {
            log.error("Ошибка при обработке callback {}: ", callbackData, e);
            answerCallbackQuery(callbackQuery.getId(),
                    "❌ Произошла ошибка при обработке запроса.", true);
        }
    }

    /**
     * Обработка callback'ов модерации
     */
    private void handleModerationCallback(CallbackQuery callbackQuery, String callbackData) {
        String[] parts = callbackData.split("_");
        if (parts.length < 3) {
            answerCallbackQuery(callbackQuery.getId(),
                    "❌ Некорректный формат команды.", true);
            return;
        }

        String action = parts[1]; // approve, reject, block, details
        Long imageId;

        try {
            imageId = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            answerCallbackQuery(callbackQuery.getId(),
                    "❌ Некорректный ID изображения.", true);
            return;
        }

        Long moderatorId = callbackQuery.getFrom().getId();

        switch (action) {
            case "approve" -> {
                moderationService.processModerationDecision(
                        imageId, moderatorId, ImageStatus.APPROVED, null);

                // Убираем кнопки и показываем результат
                removeInlineKeyboard(callbackQuery);
                answerCallbackQuery(callbackQuery.getId(),
                        "✅ Изображение одобрено!", false);
            }
            case "reject" -> {
                moderationService.processModerationDecision(
                        imageId, moderatorId, ImageStatus.REJECTED, "Отклонено модератором");

                removeInlineKeyboard(callbackQuery);
                answerCallbackQuery(callbackQuery.getId(),
                        "❌ Изображение отклонено!", false);
            }
            case "block" -> {
                moderationService.processModerationDecision(
                        imageId, moderatorId, ImageStatus.BLOCKED, "Нарушение правил сообщества");

                removeInlineKeyboard(callbackQuery);
                answerCallbackQuery(callbackQuery.getId(),
                        "🚫 Изображение заблокировано!", false);
            }
            case "details" -> {
                moderationService.sendImageDetails(imageId, moderatorId);
                answerCallbackQuery(callbackQuery.getId(),
                        "ℹ️ Детали отправлены отдельным сообщением.", false);
            }
            default -> {
                answerCallbackQuery(callbackQuery.getId(),
                        "❌ Неизвестное действие модерации.", true);
            }
        }
    }

    /**
     * Обработка callback'ов расписания
     */
    private void handleScheduleCallback(CallbackQuery callbackQuery, String callbackData) {
        // Здесь можно добавить обработку callback'ов для настройки расписания
        // Например: schedule_set_daily_12, schedule_cancel, etc.

        String[] parts = callbackData.split("_");
        if (parts.length < 2) {
            answerCallbackQuery(callbackQuery.getId(),
                    "❌ Некорректный формат команды расписания.", true);
            return;
        }

        String action = parts[1];
        Long userId = callbackQuery.getFrom().getId();

        switch (action) {
            case "daily" -> {
                if (parts.length >= 3) {
                    String hour = parts[2];
                    // Логика установки ежедневного расписания
                    answerCallbackQuery(callbackQuery.getId(),
                            "⏰ Установлено ежедневное расписание на " + hour + ":00", false);
                }
            }
            case "cancel" -> {
                // Логика отмены расписания
                answerCallbackQuery(callbackQuery.getId(),
                        "❌ Настройка расписания отменена.", false);
            }
            default -> {
                answerCallbackQuery(callbackQuery.getId(),
                        "❌ Неизвестное действие расписания.", true);
            }
        }
    }

    /**
     * Убрать inline клавиатуру из сообщения
     */
    private void removeInlineKeyboard(CallbackQuery callbackQuery) {
        try {
            EditMessageReplyMarkup editMarkup = EditMessageReplyMarkup.builder()
                    .chatId(callbackQuery.getMessage().getChatId().toString())
                    .messageId(callbackQuery.getMessage().getMessageId())
                    .replyMarkup(InlineKeyboardMarkup.builder()
                            .keyboard(createProcessedKeyboard())
                            .build())
                    .build();

            telegramBot.execute(editMarkup);
        } catch (TelegramApiException e) {
            log.error("Ошибка при удалении inline клавиатуры: ", e);
        }
    }

    /**
     * Создать клавиатуру для обработанного изображения
     */
    private List<List<InlineKeyboardButton>> createProcessedKeyboard() {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        row.add(InlineKeyboardButton.builder()
                .text("✅ Обработано")
                .callbackData("processed")
                .build());

        keyboard.add(row);
        return keyboard;
    }

    /**
     * Отправить ответ на callback запрос
     */
    private void answerCallbackQuery(String callbackQueryId, String text, boolean showAlert) {
        try {
            AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQueryId)
                    .text(text)
                    .showAlert(showAlert)
                    .build();

            telegramBot.execute(answer);
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке ответа на callback запрос: ", e);
        }
    }
}
