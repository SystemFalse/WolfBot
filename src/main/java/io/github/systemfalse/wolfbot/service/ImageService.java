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

import io.github.systemfalse.wolfbot.model.ImageStatus;
import io.github.systemfalse.wolfbot.model.WolfImage;
import io.github.systemfalse.wolfbot.repository.WolfImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageService {

    private final WolfImageRepository wolfImageRepository;
    private final Random random = new Random();

    /**
     * Сохранить изображение
     */
    @Transactional
    public WolfImage saveImage(WolfImage image) {
        WolfImage saved = wolfImageRepository.save(image);
        log.info("Сохранено изображение ID: {} от пользователя {}",
                saved.getId(), saved.getUploadedBy().getTelegramId());
        return saved;
    }

    /**
     * Получить случайное одобренное изображение
     */
    public Optional<WolfImage> getRandomApprovedImage() {
        List<WolfImage> approvedImages = wolfImageRepository.findByStatusOrderByLastSentAsc(ImageStatus.APPROVED);

        if (approvedImages.isEmpty()) {
            log.warn("Нет одобренных изображений для отправки");
            return Optional.empty();
        }

        // Выбираем изображение, которое давно не отправлялось
        WolfImage selectedImage = approvedImages.get(0); // Первое в списке - давно не отправлялось

        // Если есть изображения, которые никогда не отправлялись, предпочитаем их
        Optional<WolfImage> neverSent = approvedImages.stream()
                .filter(img -> img.getLastSent() == null)
                .findFirst();

        if (neverSent.isPresent()) {
            selectedImage = neverSent.get();
        }

        // Отмечаем как отправленное
        selectedImage.markAsSent();
        wolfImageRepository.save(selectedImage);

        log.debug("Выбрано изображение для отправки: ID {}", selectedImage.getId());
        return Optional.of(selectedImage);
    }

    /**
     * Получить количество изображений пользователя за последний час
     */
    public long countUserImagesLastHour(Long userId) {
        LocalDateTime hourAgo = LocalDateTime.now().minusHours(1);
        return wolfImageRepository.countByUploadedByTelegramIdAndUploadedAtAfter(userId, hourAgo);
    }

    /**
     * Получить количество изображений пользователя в статусе PENDING
     */
    public long countUserPendingImages(Long userId) {
        return wolfImageRepository.countByUploadedByTelegramIdAndStatus(userId, ImageStatus.PENDING);
    }

    /**
     * Получить изображения для модерации
     */
    public List<WolfImage> getPendingImages() {
        return wolfImageRepository.findByStatusOrderByUploadedAtAsc(ImageStatus.PENDING);
    }

    /**
     * Получить изображение по ID
     */
    public Optional<WolfImage> getImageById(Long imageId) {
        return wolfImageRepository.findById(imageId);
    }

    /**
     * Получить статистику изображений
     */
    public ImageStats getImageStats() {
        long totalImages = wolfImageRepository.count();
        long approvedImages = wolfImageRepository.countByStatus(ImageStatus.APPROVED);
        long pendingImages = wolfImageRepository.countByStatus(ImageStatus.PENDING);
        long rejectedImages = wolfImageRepository.countByStatus(ImageStatus.REJECTED);
        long blockedImages = wolfImageRepository.countByStatus(ImageStatus.BLOCKED);

        return new ImageStats(totalImages, approvedImages, pendingImages, rejectedImages, blockedImages);
    }

    /**
     * Статистика изображений
     */
    public record ImageStats(
            long totalImages,
            long approvedImages,
            long pendingImages,
            long rejectedImages,
            long blockedImages
    ) {}
}
