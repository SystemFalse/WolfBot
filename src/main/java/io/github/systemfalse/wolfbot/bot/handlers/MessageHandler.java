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
import io.github.systemfalse.wolfbot.model.User;
import io.github.systemfalse.wolfbot.service.ScheduleService;
import io.github.systemfalse.wolfbot.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessageHandler {

    private TelegramBot telegramBot;
    private final UserService userService;
    private final ScheduleService scheduleService;

    public void initBot(TelegramBot telegramBot) {
        this.telegramBot = telegramBot;
    }

    public void handleTextMessage(Message message) {
        String text = message.getText();
        Long userId = message.getFrom().getId();
        Long chatId = message.getChatId();

        log.info("Получено сообщение от пользователя {}: {}", userId, text);

        // Регистрируем пользователя если он новый
        User user = userService.findOrCreateUser(message.getFrom());

        // Обработка команд
        switch (text) {
            case "/start":
                handleStartCommand(chatId, user);
                break;
            case "/help":
                handleHelpCommand(chatId);
                break;
            case "/subscribe":
                handleSubscribeCommand(chatId, user);
                break;
            case "/unsubscribe":
                handleUnsubscribeCommand(chatId, user);
                break;
            case "/schedule":
                handleScheduleCommand(chatId, user);
                break;
            case "/upload":
                handleUploadCommand(chatId);
                break;
            case "/status":
                handleStatusCommand(chatId, user);
                break;
            default:
                handleUnknownCommand(chatId, text);
        }
    }

    private void handleStartCommand(Long chatId, User user) {
        String welcomeText = String.format(
                "🐺 <b>Добро пожаловать в Wolf Bot!</b>\n\n" +
                        "Привет, %s! Я бот для организации картинок волков.\n\n" +
                        "🔧 <b>Мои возможности:</b>\n" +
                        "• Отправка картинок волков по расписанию\n" +
                        "• Загрузка новых изображений\n" +
                        "• Настройка персонального расписания\n\n" +
                        "📝 Используй /help для просмотра всех команд",
                user.getDisplayName()
        );
        telegramBot.sendTextMessage(chatId, welcomeText);
    }

    private void handleHelpCommand(Long chatId) {
        String helpText =
                "🤖 <b>Список команд:</b>\n\n" +
                        "/start - Начать работу с ботом\n" +
                        "/help - Показать эту справку\n" +
                        "/subscribe - Подписаться на рассылку\n" +
                        "/unsubscribe - Отписаться от рассылки\n" +
                        "/schedule - Настроить расписание\n" +
                        "/upload - Загрузить картинку волка\n" +
                        "/status - Показать мой статус\n\n" +
                        "📸 Чтобы загрузить картинку, просто отправь фото в чат!\n\n" +
                        "❓ Если нужна помощь, обратись к администратору.";
        telegramBot.sendTextMessage(chatId, helpText);
    }

    private void handleSubscribeCommand(Long chatId, User user) {
        if (user.isSubscribed()) {
            telegramBot.sendTextMessage(chatId,
                    "✅ Вы уже подписаны на рассылку картинок волков!");
        } else {
            userService.updateSubscription(user.getTelegramId(), true);
            telegramBot.sendTextMessage(chatId,
                    "🎉 <b>Поздравляем!</b>\n\n" +
                            "Вы успешно подписались на рассылку картинок волков!\n" +
                            "По умолчанию картинки будут приходить каждый день в 12:00.\n\n" +
                            "Используйте /schedule для настройки персонального расписания.");
        }
    }

    private void handleUnsubscribeCommand(Long chatId, User user) {
        if (!user.isSubscribed()) {
            telegramBot.sendTextMessage(chatId,
                    "ℹ️ Вы уже отписаны от рассылки.");
        } else {
            userService.updateSubscription(user.getTelegramId(), false);
            telegramBot.sendTextMessage(chatId,
                    "😢 Вы отписались от рассылки картинок волков.\n\n" +
                            "Чтобы снова подписаться, используйте команду /subscribe");
        }
    }

    private void handleScheduleCommand(Long chatId, User user) {
        String scheduleText =
                "⏰ <b>Настройка расписания</b>\n\n" +
                        "Доступные варианты:\n" +
                        "• Каждый день в 9:00 - /set_daily_9\n" +
                        "• Каждый day в 12:00 - /set_daily_12\n" +
                        "• Каждый день в 18:00 - /set_daily_18\n" +
                        "• Только по рабочим дням в 12:00 - /set_workdays\n" +
                        "• Только по выходным в 10:00 - /set_weekends\n\n" +
                        "🔧 Для настройки своего расписания обратитесь к администратору.";
        telegramBot.sendTextMessage(chatId, scheduleText);
    }

    private void handleUploadCommand(Long chatId) {
        telegramBot.sendTextMessage(chatId,
                "📸 <b>Загрузка изображения</b>\n\n" +
                        "Отправьте фотографию волка в чат, и она будет добавлена на модерацию.\n\n" +
                        "⚠️ <b>Требования:</b>\n" +
                        "• Размер файла не более 10 МБ\n" +
                        "• Только изображения волков\n" +
                        "• Качественные фотографии\n\n" +
                        "После модерации ваша картинка будет добавлена в общую базу.");
    }

    private void handleStatusCommand(Long chatId, User user) {
        String status = user.isSubscribed() ? "✅ Подписан" : "❌ Не подписан";
        long uploadedCount = userService.getUserUploadedImagesCount(user.getTelegramId());

        String statusText = String.format(
                "👤 <b>Ваш статус</b>\n\n" +
                        "Имя: %s\n" +
                        "Подписка: %s\n" +
                        "Загружено картинок: %d\n" +
                        "Дата регистрации: %s",
                user.getDisplayName(),
                status,
                uploadedCount,
                user.getRegisteredAt().toLocalDate()
        );
        telegramBot.sendTextMessage(chatId, statusText);
    }

    private void handleUnknownCommand(Long chatId, String text) {
        telegramBot.sendTextMessage(chatId,
                "❓ Неизвестная команда: " + text + "\n\n" +
                        "Используйте /help для просмотра доступных команд.");
    }
}
