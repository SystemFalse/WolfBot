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

import io.github.systemfalse.wolfbot.model.User;
import io.github.systemfalse.wolfbot.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    /**
     * Найти или создать пользователя
     */
    @Transactional
    public User findOrCreateUser(org.telegram.telegrambots.meta.api.objects.User telegramUser) {
        Optional<User> existingUser = userRepository.findById(telegramUser.getId());

        if (existingUser.isPresent()) {
            User user = existingUser.get();
            // Обновляем информацию о пользователе
            updateUserInfo(user, telegramUser);
            return userRepository.save(user);
        } else {
            // Создаем нового пользователя
            User newUser = User.builder()
                    .telegramId(telegramUser.getId())
                    .username(telegramUser.getUserName())
                    .firstName(telegramUser.getFirstName())
                    .lastName(telegramUser.getLastName())
                    .subscribed(false)
                    .build();
            User savedUser = userRepository.save(newUser);
            log.info("Создан новый пользователь: {} (ID: {})",
                    savedUser.getDisplayName(), savedUser.getTelegramId());
            return savedUser;
        }
    }

    /**
     * Обновить информацию о пользователе
     */
    private void updateUserInfo(User user, org.telegram.telegrambots.meta.api.objects.User telegramUser) {
        boolean updated = false;

        if (!Objects.equals(user.getUsername(), telegramUser.getUserName())) {
            user.setUsername(telegramUser.getUserName());
            updated = true;
        }

        if (!Objects.equals(user.getFirstName(), telegramUser.getFirstName())) {
            user.setFirstName(telegramUser.getFirstName());
            updated = true;
        }

        if (!Objects.equals(user.getLastName(), telegramUser.getLastName())) {
            user.setLastName(telegramUser.getLastName());
            updated = true;
        }

        if (updated) {
            log.debug("Обновлена информация пользователя: {}", user.getTelegramId());
        }
    }

    /**
     * Обновить статус подписки пользователя
     */
    @Transactional
    public void updateSubscription(Long telegramId, boolean subscribed) {
        userRepository.updateSubscriptionStatus(telegramId, subscribed);
        log.info("Обновлен статус подписки пользователя {}: {}", telegramId, subscribed);
    }

    /**
     * Обновить время последней активности пользователя
     */
    @Transactional
    public void updateUserActivity(Long telegramId) {
        userRepository.updateLastActivity(telegramId);
    }

    /**
     * Получить всех подписанных пользователей
     */
    public List<User> getSubscribedUsers() {
        return userRepository.findBySubscribedTrue();
    }

    /**
     * Получить количество загруженных пользователем изображений
     */
    public long getUserUploadedImagesCount(Long telegramId) {
        return userRepository.countUploadedImages(telegramId);
    }

    /**
     * Найти пользователя по Telegram ID
     */
    public Optional<User> findUserById(Long telegramId) {
        return userRepository.findById(telegramId);
    }

    /**
     * Получить статистику пользователей
     */
    public UserStats getUserStats() {
        long totalUsers = userRepository.count();
        long subscribedUsers = userRepository.countBySubscribedTrue();
        return new UserStats(totalUsers, subscribedUsers);
    }

    /**
     * Статистика пользователей
     */
    public record UserStats(long totalUsers, long subscribedUsers) {}

    /**
     * Получить пользователей, зарегистрированных сегодня (метод в сервисе)
     */
    public List<User> getUsersRegisteredToday() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        return userRepository.findUsersRegisteredToday(startOfDay, endOfDay);
    }

    /**
     * Получить активных пользователей за сегодня (метод в сервисе)
     */
    public List<User> getUsersActiveToday() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        return userRepository.findUsersActiveToday(startOfDay, endOfDay);
    }

    /**
     * Получить статистику регистраций по дням
     */
    public List<DailyStats> getRegistrationStatsByDay(int daysBack) {
        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);
        List<Object[]> results = userRepository.getUserRegistrationStatsByDay(since);

        return results.stream()
                .map(row -> new DailyStats((LocalDate) row[0], (Long) row[1]))
                .toList();
    }

    /**
     * DTO для ежедневной статистики
     */
    public record DailyStats(LocalDate date, Long count) {}
}
