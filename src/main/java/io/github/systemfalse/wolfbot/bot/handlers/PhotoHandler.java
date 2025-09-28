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
import io.github.systemfalse.wolfbot.config.BotConfig;
import io.github.systemfalse.wolfbot.model.User;
import io.github.systemfalse.wolfbot.model.WolfImage;
import io.github.systemfalse.wolfbot.service.ImageService;
import io.github.systemfalse.wolfbot.service.ModerationService;
import io.github.systemfalse.wolfbot.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PhotoHandler {

    private TelegramBot telegramBot;
    private final ImageService imageService;
    private final UserService userService;
    private final ModerationService moderationService;
    private final BotConfig botConfig;

    public void initBot(TelegramBot telegramBot) {
        this.telegramBot = telegramBot;
        moderationService.initBot(telegramBot);
    }

    /**
     * Обработка загруженной фотографии
     */
    public void handlePhoto(Message message) {
        Long userId = message.getFrom().getId();
        Long chatId = message.getChatId();

        log.info("Получена фотография от пользователя: {}", userId);

        try {
            // Получаем пользователя
            User user = userService.findOrCreateUser(message.getFrom());

            // Проверяем лимиты пользователя
            if (!checkUserLimits(user, chatId)) {
                return;
            }

            // Получаем фотографию наилучшего качества
            PhotoSize photo = getBestQualityPhoto(message.getPhoto());
            if (photo == null) {
                telegramBot.sendTextMessage(chatId,
                        "❌ Ошибка при обработке фотографии. Попробуйте еще раз.");
                return;
            }

            // Проверяем размер файла
            if (photo.getFileSize() > botConfig.getMaxFileSize()) {
                telegramBot.sendTextMessage(chatId,
                        String.format("❌ Размер файла слишком большой (%.1f МБ). " +
                                        "Максимальный размер: %.1f МБ",
                                photo.getFileSize() / 1024.0 / 1024.0,
                                botConfig.getMaxFileSize() / 1024.0 / 1024.0));
                return;
            }

            // Загружаем файл с серверов Telegram
            byte[] imageData = downloadPhoto(photo);
            if (imageData == null) {
                telegramBot.sendTextMessage(chatId,
                        "❌ Не удалось загрузить фотографию. Попробуйте еще раз.");
                return;
            }

            // Проверяем тип файла
            String mimeType = detectMimeType(imageData);
            if (!isValidImageType(mimeType)) {
                telegramBot.sendTextMessage(chatId,
                        "❌ Неподдерживаемый формат изображения. " +
                                "Поддерживаются: JPG, PNG, WebP");
                return;
            }

            // Создаем объект изображения
            WolfImage wolfImage = WolfImage.builder()
                    .fileName(generateFileName(photo.getFileId(), mimeType))
                    .fileData(imageData)
                    .fileSize((long) imageData.length)
                    .mimeType(mimeType)
                    .uploadedBy(user)
                    .build();

            // Сохраняем изображение
            WolfImage savedImage = imageService.saveImage(wolfImage);

            // Отправляем на модерацию
            moderationService.submitForModeration(savedImage);

            // Уведомляем пользователя
            String successMessage = String.format(
                    "✅ <b>Фотография загружена!</b>\n\n" +
                            "📸 Размер: %.1f КБ\n" +
                            "🔍 Статус: Ожидает модерации\n" +
                            "⏳ Время загрузки: %s\n\n" +
                            "Ваша фотография будет проверена модераторами и, " +
                            "при одобрении, добавлена в общую коллекцию.",
                    imageData.length / 1024.0,
                    java.time.LocalDateTime.now().format(
                            java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
            );

            telegramBot.sendTextMessage(chatId, successMessage);

            log.info("Фотография успешно обработана. ID: {}, пользователь: {}",
                    savedImage.getId(), userId);

        } catch (Exception e) {
            log.error("Ошибка при обработке фотографии от пользователя {}: ", userId, e);
            telegramBot.sendTextMessage(chatId,
                    "❌ Произошла ошибка при обработке фотографии. " +
                            "Пожалуйста, попробуйте еще раз позже.");
        }
    }

    /**
     * Проверка лимитов пользователя
     */
    private boolean checkUserLimits(User user, Long chatId) {
        // Проверяем количество загруженных изображений за последний час
        long imagesLastHour = imageService.countUserImagesLastHour(user.getTelegramId());
        int maxImagesPerHour = 5; // Можно вынести в конфигурацию

        if (imagesLastHour >= maxImagesPerHour) {
            telegramBot.sendTextMessage(chatId,
                    String.format("⚠️ Превышен лимит загрузки изображений.\n" +
                            "Максимум %d изображений в час.\n" +
                            "Попробуйте позже.", maxImagesPerHour));
            return false;
        }

        // Проверяем количество изображений в статусе PENDING
        long pendingImages = imageService.countUserPendingImages(user.getTelegramId());
        int maxPendingImages = 3; // Максимум ожидающих модерации

        if (pendingImages >= maxPendingImages) {
            telegramBot.sendTextMessage(chatId,
                    String.format("⏳ У вас слишком много изображений ожидает модерации (%d).\n" +
                            "Дождитесь проверки уже загруженных изображений.", pendingImages));
            return false;
        }

        return true;
    }

    /**
     * Получить фотографию наилучшего качества
     */
    private PhotoSize getBestQualityPhoto(List<PhotoSize> photos) {
        if (photos == null || photos.isEmpty()) {
            return null;
        }

        return photos.stream()
                .max(Comparator.comparing(PhotoSize::getFileSize))
                .orElse(photos.get(photos.size() - 1));
    }

    /**
     * Загрузить фотографию с серверов Telegram
     */
    private byte[] downloadPhoto(PhotoSize photo) {
        try {
            // Получаем информацию о файле
            GetFile getFile = GetFile.builder()
                    .fileId(photo.getFileId())
                    .build();

            File file = telegramBot.execute(getFile);

            // Формируем URL для загрузки
            String fileUrl = "https://api.telegram.org/file/bot" +
                    telegramBot.getBotToken() + "/" + file.getFilePath();

            // Загружаем файл
            try (InputStream inputStream = new URL(fileUrl).openStream()) {
                byte[] imageData = IOUtils.toByteArray(inputStream);
                log.debug("Загружен файл размером {} байт", imageData.length);
                return imageData;
            }

        } catch (TelegramApiException e) {
            log.error("Ошибка Telegram API при загрузке файла: ", e);
        } catch (IOException e) {
            log.error("Ошибка IO при загрузке файла: ", e);
        }

        return null;
    }

    /**
     * Определить MIME тип изображения
     */
    private String detectMimeType(byte[] imageData) {
        if (imageData.length < 4) {
            return "application/octet-stream";
        }

        // Проверяем сигнатуры файлов
        if (imageData[0] == (byte) 0xFF && imageData[1] == (byte) 0xD8) {
            return "image/jpeg";
        }

        if (imageData[0] == (byte) 0x89 && imageData[1] == (byte) 0x50 &&
                imageData[2] == (byte) 0x4E && imageData[3] == (byte) 0x47) {
            return "image/png";
        }

        if (imageData[0] == (byte) 0x52 && imageData[1] == (byte) 0x49 &&
                imageData[2] == (byte) 0x46 && imageData[3] == (byte) 0x46 &&
                imageData.length > 12 &&
                imageData[8] == (byte) 0x57 && imageData[9] == (byte) 0x45 &&
                imageData[10] == (byte) 0x42 && imageData[11] == (byte) 0x50) {
            return "image/webp";
        }

        return "application/octet-stream";
    }

    /**
     * Проверить, поддерживается ли тип изображения
     */
    private boolean isValidImageType(String mimeType) {
        return mimeType.equals("image/jpeg") ||
                mimeType.equals("image/png") ||
                mimeType.equals("image/webp");
    }

    /**
     * Сгенерировать имя файла
     */
    private String generateFileName(String fileId, String mimeType) {
        String extension = switch (mimeType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };

        return "wolf_" + fileId + "_" + System.currentTimeMillis() + extension;
    }
}
