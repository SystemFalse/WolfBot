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

import io.github.systemfalse.wolfbot.model.Moderator;
import io.github.systemfalse.wolfbot.service.ModeratorService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ModeratorRepository extends JpaRepository<Moderator, Long> {

    /**
     * Найти модератора по Telegram ID
     */
    Optional<Moderator> findByTelegramId(Long telegramId);

    /**
     * Проверить существование модератора по Telegram ID
     */
    boolean existsByTelegramId(Long telegramId);

    /**
     * Найти активных модераторов
     */
    List<Moderator> findByActiveTrueOrderByAddedAtDesc();

    /**
     * Найти всех модераторов с сортировкой по дате добавления
     */
    List<Moderator> findAllByOrderByAddedAtDesc();

    /**
     * Подсчет активных модераторов
     */
    long countByActiveTrue();

    /**
     * Получить статистику модератора - ИСПРАВЛЕНО
     */
    @Query("SELECT new io.github.systemfalse.wolfbot.service.ModeratorService$ModeratorStats(" +
            "m.moderationCount, " +
            "COALESCE((SELECT COUNT(wi1) FROM WolfImage wi1 WHERE wi1.moderatedBy.id = m.id AND wi1.status = 'APPROVED'), 0L), " +
            "COALESCE((SELECT COUNT(wi2) FROM WolfImage wi2 WHERE wi2.moderatedBy.id = m.id AND wi2.status = 'REJECTED'), 0L), " +
            "COALESCE((SELECT COUNT(wi3) FROM WolfImage wi3 WHERE wi3.moderatedBy.id = m.id AND wi3.status = 'BLOCKED'), 0L), " +
            "COALESCE((SELECT COUNT(wi4) FROM WolfImage wi4 WHERE wi4.moderatedBy.id = m.id AND wi4.moderatedAt >= :monthAgo), 0L), " +
            "COALESCE((SELECT COUNT(wi5) FROM WolfImage wi5 WHERE wi5.moderatedBy.id = m.id AND wi5.moderatedAt >= :dayStart AND wi5.moderatedAt < :dayEnd), 0L)" +
            ") FROM Moderator m WHERE m.telegramId = :telegramId")
    ModeratorService.ModeratorStats getModeratorStats(@Param("telegramId") Long telegramId,
                                                      @Param("monthAgo") LocalDateTime monthAgo,
                                                      @Param("dayStart") LocalDateTime dayStart,
                                                      @Param("dayEnd") LocalDateTime dayEnd);

    /**
     * Получить статистику модератора (перегруженная версия) - ИСПРАВЛЕНО
     */
    default ModeratorService.ModeratorStats getModeratorStats(Long telegramId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthAgo = now.minusDays(30);
        LocalDateTime dayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);

        return getModeratorStats(telegramId, monthAgo, dayStart, dayEnd);
    }
}
