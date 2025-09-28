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

import io.github.systemfalse.wolfbot.model.Moderator;
import io.github.systemfalse.wolfbot.repository.ModeratorRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModeratorService {

    private final ModeratorRepository moderatorRepository;

    /**
     * Добавить нового модератора
     */
    @Transactional
    public Moderator addModerator(Long telegramId, String username, String firstName) {
        // Проверяем, не существует ли уже модератор с таким ID
        if (moderatorRepository.existsByTelegramId(telegramId)) {
            throw new IllegalArgumentException("Модератор с таким Telegram ID уже существует");
        }

        Moderator moderator = Moderator.builder()
                .telegramId(telegramId)
                .username(username)
                .firstName(firstName)
                .active(true)
                .build();

        Moderator saved = moderatorRepository.save(moderator);
        log.info("Добавлен новый модератор: {} (ID: {})", saved.getDisplayName(), telegramId);
        return saved;
    }

    /**
     * Удалить модератора
     */
    @Transactional
    public void removeModerator(Long telegramId) {
        Moderator moderator = moderatorRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new IllegalArgumentException("Модератор не найден: " + telegramId));

        moderatorRepository.delete(moderator);
        log.info("Удален модератор: {} (ID: {})", moderator.getDisplayName(), telegramId);
    }

    /**
     * Активировать/деактивировать модератора
     */
    @Transactional
    public boolean setModeratorActive(Long telegramId, boolean active) {
        Optional<Moderator> moderatorOpt = moderatorRepository.findByTelegramId(telegramId);
        if (moderatorOpt.isEmpty()) {
            return false;
        }

        Moderator moderator = moderatorOpt.get();
        moderator.setActive(active);
        moderatorRepository.save(moderator);

        log.info("Модератор {} {}: {}",
                moderator.getDisplayName(),
                active ? "активирован" : "деактивирован",
                telegramId);
        return true;
    }

    /**
     * Найти модератора по Telegram ID
     */
    public Optional<Moderator> findByTelegramId(Long telegramId) {
        return moderatorRepository.findByTelegramId(telegramId);
    }

    /**
     * Получить всех модераторов
     */
    public List<Moderator> getAllModerators() {
        return moderatorRepository.findAllByOrderByAddedAtDesc();
    }

    /**
     * Получить активных модераторов
     */
    public List<Moderator> getActiveModerators() {
        return moderatorRepository.findByActiveTrueOrderByAddedAtDesc();
    }

    /**
     * Получить статистику модератора
     */
    public ModeratorStats getModeratorStats(Long telegramId) {
        return moderatorRepository.getModeratorStats(telegramId);
    }

    /**
     * Экспортировать список модераторов в CSV
     */
    public String exportModerators() throws IOException {
        List<Moderator> moderators = getAllModerators();
        String filename = "moderators_export_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) +
                ".csv";

        try (FileWriter writer = new FileWriter(filename)) {
            // Заголовки CSV
            writer.append("ID,Telegram_ID,Username,First_Name,Active,Added_At,Moderation_Count\n");

            // Данные модераторов
            for (Moderator moderator : moderators) {
                writer.append(String.format("%d,%d,%s,%s,%s,%s,%d\n",
                        moderator.getId(),
                        moderator.getTelegramId(),
                        moderator.getUsername() != null ? moderator.getUsername() : "",
                        moderator.getFirstName() != null ? moderator.getFirstName() : "",
                        moderator.getActive(),
                        moderator.getAddedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                        moderator.getModerationCount()));
            }
        }

        return filename;
    }

    /**
     * Статистика модератора
     */
    public record ModeratorStats(
            long totalModerations,
            long approvedCount,
            long rejectedCount,
            long blockedCount,
            long moderationsLastMonth,
            long moderationsToday
    ) {}
}
