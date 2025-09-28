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

package io.github.systemfalse.wolfbot.repository;

import io.github.systemfalse.wolfbot.model.ImageStatus;
import io.github.systemfalse.wolfbot.model.WolfImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.awt.print.Pageable;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WolfImageRepository extends JpaRepository<WolfImage, Long> {

    /**
     * Найти изображения по статусу с сортировкой по дате загрузки
     */
    List<WolfImage> findByStatusOrderByUploadedAtAsc(ImageStatus status);

    /**
     * Найти изображения по статусу с сортировкой по дате последней отправки
     */
    List<WolfImage> findByStatusOrderByLastSentAsc(ImageStatus status);

    /**
     * Количество изображений по статусу
     */
    long countByStatus(ImageStatus status);

    /**
     * Количество изображений пользователя за период
     */
    long countByUploadedByTelegramIdAndUploadedAtAfter(Long userId, LocalDateTime since);

    /**
     * Количество изображений пользователя по статусу
     */
    long countByUploadedByTelegramIdAndStatus(Long userId, ImageStatus status);

    /**
     * Найти изображения пользователя
     */
    List<WolfImage> findByUploadedByTelegramIdOrderByUploadedAtDesc(Long userId);

    /**
     * Найти изображения для модерации (старые сначала)
     */
    @Query("SELECT wi FROM WolfImage wi WHERE wi.status = 'PENDING' ORDER BY wi.uploadedAt ASC")
    List<WolfImage> findImagesForModeration();

    /**
     * Найти самые популярные изображения (по количеству отправок)
     */
    @Query("SELECT wi FROM WolfImage wi WHERE wi.status = 'APPROVED' ORDER BY wi.sendCount DESC")
    List<WolfImage> findMostPopularImages();

    /**
     * Найти изображения, которые давно не отправлялись
     */
    @Query("SELECT wi FROM WolfImage wi WHERE wi.status = 'APPROVED' AND " +
            "(wi.lastSent IS NULL OR wi.lastSent < :staleTime) ORDER BY wi.lastSent ASC NULLS FIRST")
    List<WolfImage> findStaleImages(@Param("staleTime") LocalDateTime staleTime);

    /**
     * Найти изображения по статусу и дате загрузки
     */
    @Query("SELECT wi FROM WolfImage wi WHERE wi.status = :status AND wi.uploadedAt < :uploadedBefore")
    List<WolfImage> findByStatusAndUploadedAtBefore(@Param("status") ImageStatus status,
                                                    @Param("uploadedBefore") LocalDateTime uploadedBefore);

    /**
     * Подсчет всех изображений пользователя
     */
    long countByUploadedByTelegramId(Long userId);

    /**
     * Найти последние изображения пользователя
     */
    @Query("SELECT wi FROM WolfImage wi WHERE wi.uploadedBy.telegramId = :userId ORDER BY wi.uploadedAt DESC")
    List<WolfImage> findLatestByUser(@Param("userId") Long userId, Pageable pageable);
}
