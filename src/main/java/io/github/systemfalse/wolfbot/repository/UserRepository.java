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

import io.github.systemfalse.wolfbot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Найти пользователя по username
     */
    Optional<User> findByUsername(String username);

    /**
     * Найти всех подписанных пользователей
     */
    List<User> findBySubscribedTrue();

    /**
     * Найти активных пользователей (активны за последние N дней)
     */
    @Query("SELECT u FROM User u WHERE u.lastActive > :since")
    List<User> findActiveUsersSince(@Param("since") LocalDateTime since);

    /**
     * Найти пользователей, зарегистрированных за период
     */
    @Query("SELECT u FROM User u WHERE u.registeredAt BETWEEN :start AND :end")
    List<User> findUsersRegisteredBetween(@Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);

    /**
     * Получить количество подписанных пользователей
     */
    long countBySubscribedTrue();

    /**
     * Получить количество пользователей, зарегистрированных за последние N дней
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.registeredAt > :since")
    long countUsersRegisteredSince(@Param("since") LocalDateTime since);

    /**
     * Получить количество активных пользователей за последние N дней
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.lastActive > :since")
    long countActiveUsersSince(@Param("since") LocalDateTime since);

    /**
     * Обновить статус подписки пользователя
     */
    @Modifying
    @Query("UPDATE User u SET u.subscribed = :subscribed WHERE u.telegramId = :telegramId")
    void updateSubscriptionStatus(@Param("telegramId") Long telegramId,
                                  @Param("subscribed") Boolean subscribed);

    /**
     * Обновить время последней активности пользователя
     */
    @Modifying
    @Query("UPDATE User u SET u.lastActive = CURRENT_TIMESTAMP WHERE u.telegramId = :telegramId")
    void updateLastActivity(@Param("telegramId") Long telegramId);

    /**
     * Получить количество загруженных пользователем изображений
     */
    @Query("SELECT COUNT(wi) FROM WolfImage wi WHERE wi.uploadedBy.telegramId = :telegramId")
    long countUploadedImages(@Param("telegramId") Long telegramId);

    /**
     * Получить количество одобренных изображений пользователя
     */
    @Query("SELECT COUNT(wi) FROM WolfImage wi WHERE wi.uploadedBy.telegramId = :telegramId AND wi.status = 'APPROVED'")
    long countApprovedImages(@Param("telegramId") Long telegramId);

    /**
     * Получить пользователей с наибольшим количеством загруженных изображений
     */
    @Query("SELECT u FROM User u " +
            "LEFT JOIN u.uploadedImages wi " +
            "GROUP BY u.telegramId " +
            "ORDER BY COUNT(wi) DESC")
    List<User> findTopUploaders();

    /**
     * Найти неактивных пользователей (не активны дольше N дней и не подписаны)
     */
    @Query("SELECT u FROM User u WHERE " +
            "(u.lastActive IS NULL OR u.lastActive < :inactiveSince) " +
            "AND u.subscribed = false " +
            "AND u.registeredAt < :registeredBefore")
    List<User> findInactiveUsers(@Param("inactiveSince") LocalDateTime inactiveSince,
                                 @Param("registeredBefore") LocalDateTime registeredBefore);

    /**
     * Получить статистику пользователей по дням - ИСПРАВЛЕНО
     */
    @Query("SELECT CAST(u.registeredAt AS LocalDate) as date, COUNT(u) as count " +
            "FROM User u " +
            "WHERE u.registeredAt >= :since " +
            "GROUP BY CAST(u.registeredAt AS LocalDate) " +
            "ORDER BY CAST(u.registeredAt AS LocalDate)")
    List<Object[]> getUserRegistrationStatsByDay(@Param("since") LocalDateTime since);

    /**
     * Поиск пользователей по части имени или username
     */
    @Query("SELECT u FROM User u WHERE " +
            "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(u.username) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<User> searchUsers(@Param("searchTerm") String searchTerm);

    /**
     * Найти пользователей, которые давно не получали изображения
     */
    @Query("SELECT DISTINCT s.user FROM Schedule s " +
            "WHERE s.active = true " +
            "AND (s.lastExecuted IS NULL OR s.lastExecuted < :since)")
    List<User> findUsersWithStaleSchedules(@Param("since") LocalDateTime since);

    /**
     * Получить пользователей для рассылки (подписанные и активные)
     */
    @Query("SELECT u FROM User u WHERE u.subscribed = true " +
            "AND (u.lastActive IS NULL OR u.lastActive > :minActivity)")
    List<User> findUsersForNotifications(@Param("minActivity") LocalDateTime minActivity);

    /**
     * Удалить старых неактивных пользователей
     */
    @Modifying
    @Query("DELETE FROM User u WHERE " +
            "u.subscribed = false " +
            "AND (u.lastActive IS NULL OR u.lastActive < :inactiveSince) " +
            "AND u.registeredAt < :registeredBefore " +
            "AND u.telegramId NOT IN (SELECT DISTINCT wi.uploadedBy.telegramId FROM WolfImage wi)")
    int deleteInactiveUsers(@Param("inactiveSince") LocalDateTime inactiveSince,
                            @Param("registeredBefore") LocalDateTime registeredBefore);

    /**
     * Проверить существование пользователя с определенным username
     */
    boolean existsByUsername(String username);

    /**
     * Найти пользователей, у которых есть активные расписания
     */
    @Query("SELECT DISTINCT s.user FROM Schedule s WHERE s.active = true")
    List<User> findUsersWithActiveSchedules();

    /**
     * Получить топ пользователей по количеству одобренных изображений
     */
    @Query("SELECT u, COUNT(wi) as imageCount FROM User u " +
            "LEFT JOIN u.uploadedImages wi " +
            "WHERE wi.status = 'APPROVED' " +
            "GROUP BY u.telegramId " +
            "ORDER BY imageCount DESC")
    List<Object[]> findTopContributors();

    /**
     * Получить пользователей, зарегистрированных сегодня - ИСПРАВЛЕНО
     */
    @Query("SELECT u FROM User u WHERE u.registeredAt >= :startOfDay AND u.registeredAt < :endOfDay")
    List<User> findUsersRegisteredToday(@Param("startOfDay") LocalDateTime startOfDay,
                                        @Param("endOfDay") LocalDateTime endOfDay);

    /**
     * Получить активных пользователей за сегодня - ИСПРАВЛЕНО
     */
    @Query("SELECT u FROM User u WHERE u.lastActive >= :startOfDay AND u.lastActive < :endOfDay")
    List<User> findUsersActiveToday(@Param("startOfDay") LocalDateTime startOfDay,
                                    @Param("endOfDay") LocalDateTime endOfDay);

    /**
     * Получить среднее время между регистрацией и первой загрузкой изображения - ИСПРАВЛЕНО
     */
    @Query("SELECT AVG(CAST((MIN(wi.uploadedAt) - u.registeredAt) AS DOUBLE)) " +
            "FROM User u " +
            "LEFT JOIN u.uploadedImages wi " +
            "WHERE wi IS NOT NULL " +
            "GROUP BY u.telegramId")
    Double getAverageTimeToFirstUpload();

    /**
     * Найти пользователей по дате регистрации (альтернативный метод)
     */
    List<User> findByRegisteredAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Найти пользователей по последней активности
     */
    List<User> findByLastActiveBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Подсчитать пользователей по дате регистрации
     */
    long countByRegisteredAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Подсчитать активных пользователей за период
     */
    long countByLastActiveBetween(LocalDateTime start, LocalDateTime end);
}
