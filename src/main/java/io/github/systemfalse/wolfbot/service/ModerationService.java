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
import io.github.systemfalse.wolfbot.model.ImageStatus;
import io.github.systemfalse.wolfbot.model.Moderator;
import io.github.systemfalse.wolfbot.model.WolfImage;
import io.github.systemfalse.wolfbot.repository.ModeratorRepository;
import io.github.systemfalse.wolfbot.repository.WolfImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModerationService {

    private TelegramBot telegramBot;
    private final ModeratorRepository moderatorRepository;
    private final WolfImageRepository wolfImageRepository;
    private final NotificationService notificationService;

    public void initBot(TelegramBot telegramBot) {
        this.telegramBot = telegramBot;
        notificationService.initBot(telegramBot);
    }

    /**
     * Отправить изображение на модерацию
     */
    @Transactional
    public void submitForModeration(WolfImage image) {
        log.info("Отправка изображения на модерацию: ID {}, пользователь {}",
                image.getId(), image.getUploadedBy().getTelegramId());

        // Получаем всех активных модераторов
        List<Moderator> activeModerators = moderatorRepository.findByActiveTrueOrderByAddedAtDesc();

        if (activeModerators.isEmpty()) {
            log.warn("Нет активных модераторов для обработки изображения ID: {}", image.getId());
            // Можно отправить уведомление администратору или поставить в очередь
            return;
        }

        // Отправляем изображение всем активным модераторам
        for (Moderator moderator : activeModerators) {
            try {
                sendImageToModerator(image, moderator);
                log.debug("Изображение ID {} отправлено модератору {}",
                        image.getId(), moderator.getTelegramId());
            } catch (Exception e) {
                log.error("Ошибка при отправке изображения ID {} модератору {}: ",
                        image.getId(), moderator.getTelegramId(), e);
            }
        }

        log.info("Изображение ID {} отправлено {} модераторам",
                image.getId(), activeModerators.size());
    }

    /**
     * Отправить изображение конкретному модератору
     */
    private void sendImageToModerator(WolfImage image, Moderator moderator) {
        try {
            // Формируем сообщение с информацией об изображении
            String caption = buildModerationCaption(image);

            // Создаем inline клавиатуру для модерации
            InlineKeyboardMarkup keyboard = createModerationKeyboard(image.getId());

            // Создаем объект для отправки фото
            SendPhoto sendPhoto = SendPhoto.builder()
                    .chatId(moderator.getTelegramId().toString())
                    .photo(new InputFile(new ByteArrayInputStream(image.getFileData()),
                            image.getFileName()))
                    .caption(caption)
                    .parseMode("HTML")
                    .replyMarkup(keyboard)
                    .build();

            telegramBot.execute(sendPhoto);

        } catch (Exception e) {
            log.error("Ошибка при отправке изображения модератору {}: ",
                    moderator.getTelegramId(), e);
            throw new RuntimeException("Не удалось отправить изображение модератору", e);
        }
    }

    /**
     * Создать подпись для изображения на модерации
     */
    private String buildModerationCaption(WolfImage image) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

        return String.format(
                "🔍 <b>Модерация изображения</b>\n\n" +
                        "📸 <b>ID:</b> %s\n" +
                        "👤 <b>От пользователя:</b> %s\n" +
                        "📅 <b>Загружено:</b> %s\n" +
                        "📏 <b>Размер:</b> %.1f КБ\n" +
                        "🗂 <b>Тип:</b> %s\n\n" +
                        "❓ <b>Одобрить изображение для рассылки?</b>",
                image.getId(),
                image.getUploadedBy().getDisplayName(),
                image.getUploadedAt().format(formatter),
                image.getFileSize() / 1024.0,
                image.getMimeType()
        );
    }

    /**
     * Создать inline клавиатуру для модерации
     */
    private InlineKeyboardMarkup createModerationKeyboard(Long imageId) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        // Первый ряд - Одобрить/Отклонить
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(InlineKeyboardButton.builder()
                .text("✅ Одобрить")
                .callbackData("moderate_approve_" + imageId)
                .build());
        row1.add(InlineKeyboardButton.builder()
                .text("❌ Отклонить")
                .callbackData("moderate_reject_" + imageId)
                .build());

        // Второй ряд - Заблокировать
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(InlineKeyboardButton.builder()
                .text("🚫 Заблокировать (нарушение)")
                .callbackData("moderate_block_" + imageId)
                .build());

        // Третий ряд - Просмотр деталей
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(InlineKeyboardButton.builder()
                .text("ℹ️ Подробности")
                .callbackData("moderate_details_" + imageId)
                .build());

        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);

        return InlineKeyboardMarkup.builder()
                .keyboard(keyboard)
                .build();
    }

    /**
     * Обработать решение модератора
     */
    @Transactional
    public void processModerationDecision(Long imageId, Long moderatorTelegramId,
                                          ImageStatus decision, String reason) {

        log.info("Обработка решения модерации: изображение {}, модератор {}, решение {}",
                imageId, moderatorTelegramId, decision);

        // Находим изображение
        Optional<WolfImage> imageOpt = wolfImageRepository.findById(imageId);
        if (imageOpt.isEmpty()) {
            log.warn("Изображение не найдено: ID {}", imageId);
            return;
        }

        WolfImage image = imageOpt.get();

        // Проверяем, что изображение еще не промодерировано
        if (image.getStatus() != ImageStatus.PENDING) {
            log.warn("Изображение ID {} уже промодерировано: статус {}",
                    imageId, image.getStatus());
            return;
        }

        // Находим модератора
        Optional<Moderator> moderatorOpt = moderatorRepository.findByTelegramId(moderatorTelegramId);
        if (moderatorOpt.isEmpty()) {
            log.warn("Модератор не найден: Telegram ID {}", moderatorTelegramId);
            return;
        }

        Moderator moderator = moderatorOpt.get();

        // Обновляем статус изображения
        image.moderate(decision, moderator, reason);
        wolfImageRepository.save(image);

        // Увеличиваем счетчик модераций у модератора
        moderator.incrementModerationCount();
        moderatorRepository.save(moderator);

        // Уведомляем пользователя о результате модерации
        notifyUserAboutModerationResult(image);

        // Уведомляем модератора о принятом решении
        notifyModeratorAboutDecision(moderator, image, decision);

        log.info("Модерация завершена: изображение {} получило статус {}",
                imageId, decision);
    }

    /**
     * Уведомить пользователя о результате модерации
     */
    private void notifyUserAboutModerationResult(WolfImage image) {
        Long userId = image.getUploadedBy().getTelegramId();
        String message;

        switch (image.getStatus()) {
            case APPROVED -> {
                message = String.format(
                        "✅ <b>Ваше изображение одобрено!</b>\n\n" +
                                "📸 Изображение #%s прошло модерацию и добавлено в общую коллекцию.\n" +
                                "Теперь оно может быть отправлено другим пользователям!\n\n" +
                                "🎉 Спасибо за вклад в развитие бота!",
                        image.getId()
                );
            }
            case REJECTED -> {
                message = String.format(
                        "❌ <b>Ваше изображение отклонено</b>\n\n" +
                                "📸 Изображение #%s не прошло модерацию.\n" +
                                "%s\n\n" +
                                "💡 Попробуйте загрузить другое изображение волка лучшего качества.",
                        image.getId(),
                        image.getModerationReason() != null ?
                                "Причина: " + image.getModerationReason() :
                                "Изображение не соответствует требованиям."
                );
            }
            case BLOCKED -> {
                message = String.format(
                        "🚫 <b>Ваше изображение заблокировано</b>\n\n" +
                                "📸 Изображение #%s нарушает правила сообщества.\n" +
                                "%s\n\n" +
                                "⚠️ Повторные нарушения могут привести к ограничению функций бота.",
                        image.getId(),
                        image.getModerationReason() != null ?
                                "Причина: " + image.getModerationReason() :
                                "Обнаружено нарушение правил."
                );
            }
            default -> {
                log.warn("Неизвестный статус модерации: {}", image.getStatus());
                return;
            }
        }

        try {
            telegramBot.sendTextMessage(userId, message);
            log.debug("Отправлено уведомление пользователю {} о модерации изображения {}",
                    userId, image.getId());
        } catch (Exception e) {
            log.error("Ошибка при отправке уведомления пользователю {}: ", userId, e);
        }
    }

    /**
     * Уведомить модератора о принятом решении
     */
    private void notifyModeratorAboutDecision(Moderator moderator, WolfImage image, ImageStatus decision) {
        String statusText = switch (decision) {
            case APPROVED -> "✅ одобрено";
            case REJECTED -> "❌ отклонено";
            case BLOCKED -> "🚫 заблокировано";
            default -> "обработано";
        };

        String message = String.format(
                "👍 <b>Решение принято</b>\n\n" +
                        "📸 Изображение #%d %s\n" +
                        "👤 От пользователя: %s\n" +
                        "📊 Ваших модераций: %d",
                image.getId(),
                statusText,
                image.getUploadedBy().getDisplayName(),
                moderator.getModerationCount()
        );

        try {
            telegramBot.sendTextMessage(moderator.getTelegramId(), message);
        } catch (Exception e) {
            log.error("Ошибка при отправке уведомления модератору {}: ",
                    moderator.getTelegramId(), e);
        }
    }

    /**
     * Получить детальную информацию об изображении для модератора
     */
    public void sendImageDetails(Long imageId, Long moderatorTelegramId) {
        Optional<WolfImage> imageOpt = wolfImageRepository.findById(imageId);
        if (imageOpt.isEmpty()) {
            telegramBot.sendTextMessage(moderatorTelegramId,
                    "❌ Изображение не найдено или уже удалено.");
            return;
        }

        WolfImage image = imageOpt.get();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

        // Получаем статистику пользователя
        long userTotalImages = wolfImageRepository.countByUploadedByTelegramId(
                image.getUploadedBy().getTelegramId());
        long userApprovedImages = wolfImageRepository
                .countByUploadedByTelegramIdAndStatus(
                        image.getUploadedBy().getTelegramId(), ImageStatus.APPROVED);

        String detailsMessage = String.format(
                "🔍 <b>Детальная информация</b>\n\n" +
                        "📸 <b>Изображение #%s</b>\n" +
                        "📅 Загружено: %s\n" +
                        "📏 Размер: %d байт (%.1f КБ)\n" +
                        "🗂 MIME тип: %s\n" +
                        "📂 Имя файла: %s\n\n" +
                        "👤 <b>Пользователь:</b>\n" +
                        "🆔 ID: %d\n" +
                        "👤 Имя: %s\n" +
                        "📊 Всего загрузил: %d\n" +
                        "✅ Одобрено: %d\n" +
                        "📅 Регистрация: %s\n" +
                        "📱 Подписан: %s",
                image.getId(),
                image.getUploadedAt().format(formatter),
                image.getFileSize(),
                image.getFileSize() / 1024.0,
                image.getMimeType(),
                image.getFileName(),
                image.getUploadedBy().getTelegramId(),
                image.getUploadedBy().getDisplayName(),
                userTotalImages,
                userApprovedImages,
                image.getUploadedBy().getRegisteredAt().format(formatter),
                image.getUploadedBy().isSubscribed() ? "Да" : "Нет"
        );

        telegramBot.sendTextMessage(moderatorTelegramId, detailsMessage);
    }

    /**
     * Получить статистику модерации
     */
    public ModerationStats getModerationStats() {
        long totalPending = wolfImageRepository.countByStatus(ImageStatus.PENDING);
        long totalApproved = wolfImageRepository.countByStatus(ImageStatus.APPROVED);
        long totalRejected = wolfImageRepository.countByStatus(ImageStatus.REJECTED);
        long totalBlocked = wolfImageRepository.countByStatus(ImageStatus.BLOCKED);
        long activeModerators = moderatorRepository.countByActiveTrue();

        return new ModerationStats(
                totalPending, totalApproved, totalRejected, totalBlocked, activeModerators);
    }

    /**
     * Получить изображения, ожидающие модерации
     */
    public List<WolfImage> getPendingImages() {
        return wolfImageRepository.findByStatusOrderByUploadedAtAsc(ImageStatus.PENDING);
    }

    /**
     * Получить старые изображения без модерации (старше N часов)
     */
    public List<WolfImage> getStaleImages(int hoursOld) {
        LocalDateTime threshold = LocalDateTime.now().minusHours(hoursOld);
        return wolfImageRepository.findByStatusAndUploadedAtBefore(ImageStatus.PENDING, threshold);
    }

    /**
     * Напомнить модераторам о необходимости модерации
     */
    public void sendModerationReminder() {
        List<WolfImage> pendingImages = getPendingImages();
        if (pendingImages.isEmpty()) {
            return;
        }

        List<Moderator> activeModerators = moderatorRepository.findByActiveTrueOrderByAddedAtDesc();
        if (activeModerators.isEmpty()) {
            log.warn("Нет активных модераторов для отправки напоминания");
            return;
        }

        String reminderMessage = String.format(
                "⏰ <b>Напоминание о модерации</b>\n\n" +
                        "📸 Ожидает модерации: %d изображений\n" +
                        "🕐 Самое старое загружено: %s\n\n" +
                        "Пожалуйста, проверьте новые изображения в боте.",
                pendingImages.size(),
                pendingImages.getFirst().getUploadedAt().format(
                        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        );

        for (Moderator moderator : activeModerators) {
            try {
                telegramBot.sendTextMessage(moderator.getTelegramId(), reminderMessage);
            } catch (Exception e) {
                log.error("Ошибка при отправке напоминания модератору {}: ",
                        moderator.getTelegramId(), e);
            }
        }

        log.info("Отправлены напоминания {} модераторам о {} изображениях",
                activeModerators.size(), pendingImages.size());
    }

    /**
     * Проверить, является ли пользователь модератором
     */
    public boolean isModerator(Long telegramId) {
        return moderatorRepository.findByTelegramId(telegramId)
                .map(Moderator::getActive)
                .orElse(false);
    }

    /**
     * Автоматически одобрить изображение (для тестирования)
     */
    @Transactional
    public void autoApproveImage(Long imageId, String reason) {
        Optional<WolfImage> imageOpt = wolfImageRepository.findById(imageId);
        if (imageOpt.isEmpty()) {
            log.warn("Изображение для авто одобрения не найдено: ID {}", imageId);
            return;
        }

        WolfImage image = imageOpt.get();
        if (image.getStatus() != ImageStatus.PENDING) {
            log.warn("Изображение ID {} уже обработано: статус {}", imageId, image.getStatus());
            return;
        }

        // Создаем системного модератора или используем первого доступного
        Moderator systemModerator = moderatorRepository.findByActiveTrueOrderByAddedAtDesc()
                .stream()
                .findFirst()
                .orElse(null);

        if (systemModerator != null) {
            image.moderate(ImageStatus.APPROVED, systemModerator, reason);
            wolfImageRepository.save(image);

            notifyUserAboutModerationResult(image);
            log.info("Изображение ID {} автоматически одобрено", imageId);
        } else {
            log.warn("Нет доступных модераторов для авто одобрения изображения ID {}", imageId);
        }
    }

    /**
     * Статистика модерации
     */
    public record ModerationStats(
            long pendingImages,
            long approvedImages,
            long rejectedImages,
            long blockedImages,
            long activeModerators
    ) {}
}