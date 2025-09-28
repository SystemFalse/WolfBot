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

package io.github.systemfalse.wolfbot.service;

import io.github.systemfalse.wolfbot.bot.TelegramBot;
import io.github.systemfalse.wolfbot.model.User;
import io.github.systemfalse.wolfbot.model.WolfImage;
import io.github.systemfalse.wolfbot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private TelegramBot telegramBot;
    private final UserRepository userRepository;

    public void initBot(TelegramBot telegramBot) {
        this.telegramBot = telegramBot;
    }

    /**
     * Отправить уведомление всем подписанным пользователям
     */
    @Async
    public CompletableFuture<Void> notifyAllSubscribers(String message) {
        List<User> subscribers = userRepository.findBySubscribedTrue();

        log.info("Отправка уведомления {} подписчикам", subscribers.size());

        int successCount = 0;
        int errorCount = 0;

        for (User user : subscribers) {
            try {
                telegramBot.sendTextMessage(user.getTelegramId(), message);
                successCount++;

                // Небольшая пауза между отправками для избежания rate limit
                Thread.sleep(50);
            } catch (Exception e) {
                log.error("Ошибка при отправке уведомления пользователю {}: ",
                        user.getTelegramId(), e);
                errorCount++;
            }
        }

        log.info("Уведомления отправлены: успешно {}, ошибок {}", successCount, errorCount);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Отправить изображение пользователю
     */
    public boolean sendImageToUser(Long userId, WolfImage image) {
        try {
            String caption = String.format(
                    "🐺 <b>Картинка дня!</b>\n\n" +
                            "📅 %s\n" +
                            "💝 Наслаждайтесь!",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            );

            telegramBot.sendPhoto(userId, image.getFileData(), caption);

            log.debug("Изображение ID {} отправлено пользователю {}", image.getId(), userId);
            return true;
        } catch (Exception e) {
            log.error("Ошибка при отправке изображения пользователю {}: ", userId, e);
            return false;
        }
    }

    /**
     * Отправить системное уведомление администраторам
     */
    @Async
    public void notifyAdministrators(String message) {
        // Здесь можно добавить логику отправки уведомлений администраторам
        // Например, через отдельную таблицу администраторов или hardcoded ID
        log.info("Системное уведомление: {}", message);
    }

    /**
     * Отправить уведомление о статистике
     */
    public void sendDailyStats(Long adminId, String statsMessage) {
        try {
            String message = String.format(
                    "📊 <b>Ежедневная статистика</b>\n\n%s\n\n📅 %s",
                    statsMessage,
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
            );

            telegramBot.sendTextMessage(adminId, message);
        } catch (Exception e) {
            log.error("Ошибка при отправке статистики администратору {}: ", adminId, e);
        }
    }
}
