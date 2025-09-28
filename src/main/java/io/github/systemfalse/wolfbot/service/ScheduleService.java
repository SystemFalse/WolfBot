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

import io.github.systemfalse.wolfbot.model.Schedule;
import io.github.systemfalse.wolfbot.model.User;
import io.github.systemfalse.wolfbot.repository.ScheduleRepository;
import io.github.systemfalse.wolfbot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final UserRepository userRepository;

    // Предопределенные CRON выражения
    private static final Map<String, String> PREDEFINED_SCHEDULES = Map.of(
            "daily_9", "0 0 9 * * ?",      // Каждый день в 9:00
            "daily_12", "0 0 12 * * ?",    // Каждый день в 12:00
            "daily_18", "0 0 18 * * ?",    // Каждый день в 18:00
            "workdays", "0 0 12 * * MON-FRI", // Рабочие дни в 12:00
            "weekends", "0 0 10 * * SAT,SUN", // Выходные в 10:00
            "twice_daily", "0 0 9,18 * * ?",  // Два раза в день
            "hourly", "0 0 * * * ?",       // Каждый час
            "every_2h", "0 0 */2 * * ?"    // Каждые 2 часа
    );

    private static final Map<String, String> SCHEDULE_DESCRIPTIONS = Map.of(
            "daily_9", "Каждый день в 9:00",
            "daily_12", "Каждый день в 12:00",
            "daily_18", "Каждый день в 18:00",
            "workdays", "Рабочие дни (Пн-Пт) в 12:00",
            "weekends", "Выходные (Сб-Вс) в 10:00",
            "twice_daily", "Два раза в день (9:00 и 18:00)",
            "hourly", "Каждый час",
            "every_2h", "Каждые 2 часа"
    );

    /**
     * Создать расписание для пользователя
     */
    @Transactional
    public Schedule createSchedule(Long userId, String scheduleType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + userId));

        String cronExpression = PREDEFINED_SCHEDULES.get(scheduleType);
        if (cronExpression == null) {
            throw new IllegalArgumentException("Неизвестный тип расписания: " + scheduleType);
        }

        // Проверяем валидность CRON выражения
        if (!isValidCronExpression(cronExpression)) {
            throw new IllegalArgumentException("Некорректное CRON выражение: " + cronExpression);
        }

        // Деактивируем все существующие расписания пользователя
        deactivateUserSchedules(userId);

        // Создаем новое расписание
        Schedule schedule = Schedule.builder()
                .user(user)
                .cronExpression(cronExpression)
                .description(SCHEDULE_DESCRIPTIONS.get(scheduleType))
                .active(true)
                .build();

        Schedule savedSchedule = scheduleRepository.save(schedule);
        log.info("Создано расписание для пользователя {}: {} ({})",
                userId, scheduleType, cronExpression);

        return savedSchedule;
    }

    /**
     * Создать кастомное расписание
     */
    @Transactional
    public Schedule createCustomSchedule(Long userId, String cronExpression, String description) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + userId));

        if (!isValidCronExpression(cronExpression)) {
            throw new IllegalArgumentException("Некорректное CRON выражение: " + cronExpression);
        }

        // Деактивируем все существующие расписания пользователя
        deactivateUserSchedules(userId);

        Schedule schedule = Schedule.builder()
                .user(user)
                .cronExpression(cronExpression)
                .description(description != null ? description : "Кастомное расписание")
                .active(true)
                .build();

        Schedule savedSchedule = scheduleRepository.save(schedule);
        log.info("Создано кастомное расписание для пользователя {}: {}", userId, cronExpression);

        return savedSchedule;
    }

    /**
     * Получить активное расписание пользователя
     */
    public Optional<Schedule> getActiveUserSchedule(Long userId) {
        return scheduleRepository.findByUserTelegramIdAndActiveTrue(userId).stream().findFirst();
    }

    /**
     * Получить все расписания пользователя
     */
    public List<Schedule> getUserSchedules(Long userId) {
        return scheduleRepository.findByUserTelegramIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Деактивировать расписание
     */
    @Transactional
    public void deactivateSchedule(Long scheduleId) {
        scheduleRepository.findById(scheduleId).ifPresent(schedule -> {
            schedule.setActive(false);
            scheduleRepository.save(schedule);
            log.info("Деактивировано расписание: {}", scheduleId);
        });
    }

    /**
     * Деактивировать все расписания пользователя
     */
    @Transactional
    public void deactivateUserSchedules(Long userId) {
        List<Schedule> activeSchedules = scheduleRepository.findByUserTelegramIdAndActiveTrue(userId);
        activeSchedules.forEach(schedule -> schedule.setActive(false));
        scheduleRepository.saveAll(activeSchedules);

        if (!activeSchedules.isEmpty()) {
            log.info("Деактивировано {} расписаний пользователя {}", activeSchedules.size(), userId);
        }
    }

    /**
     * Получить все активные расписания
     */
    public List<Schedule> getAllActiveSchedules() {
        return scheduleRepository.findByActiveTrue();
    }

    /**
     * Отметить выполнение расписания
     */
    @Transactional
    public void markScheduleExecuted(Long scheduleId) {
        scheduleRepository.findById(scheduleId).ifPresent(schedule -> {
            schedule.markExecuted();
            scheduleRepository.save(schedule);
            log.debug("Отмечено выполнение расписания: {}", scheduleId);
        });
    }

    /**
     * Проверить, нужно ли выполнить расписание
     */
    public boolean shouldExecuteSchedule(Schedule schedule) {
        try {
            CronExpression cronExpression = CronExpression.parse(schedule.getCronExpression());
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime lastExecution = schedule.getLastExecuted();

            if (lastExecution == null) {
                // Если расписание никогда не выполнялось, проверяем, пора ли его выполнить
                LocalDateTime nextExecution = cronExpression.next(now.minusMinutes(1));
                return nextExecution != null && !nextExecution.isAfter(now);
            }

            // Проверяем, есть ли следующее выполнение между последним выполнением и сейчас
            LocalDateTime nextExecution = cronExpression.next(lastExecution);
            return nextExecution != null && !nextExecution.isAfter(now);

        } catch (Exception e) {
            log.error("Ошибка при проверке расписания {}: ", schedule.getId(), e);
            return false;
        }
    }

    /**
     * Получить следующее время выполнения расписания
     */
    public Optional<LocalDateTime> getNextExecutionTime(Schedule schedule) {
        try {
            CronExpression cronExpression = CronExpression.parse(schedule.getCronExpression());
            LocalDateTime nextExecution = cronExpression.next(LocalDateTime.now());
            return Optional.ofNullable(nextExecution);
        } catch (Exception e) {
            log.error("Ошибка при вычислении следующего времени выполнения для расписания {}: ",
                    schedule.getId(), e);
            return Optional.empty();
        }
    }

    /**
     * Валидация CRON выражения
     */
    public boolean isValidCronExpression(String cronExpression) {
        try {
            CronExpression.parse(cronExpression);
            return true;
        } catch (Exception e) {
            log.debug("Невалидное CRON выражение: {}", cronExpression);
            return false;
        }
    }

    /**
     * Получить доступные предопределенные расписания
     */
    public Map<String, String> getAvailableScheduleTypes() {
        return new HashMap<>(SCHEDULE_DESCRIPTIONS);
    }

    /**
     * Создать расписание по умолчанию для нового подписчика
     */
    @Transactional
    public void createDefaultScheduleForNewSubscriber(Long userId) {
        // Проверяем, есть ли уже активное расписание
        Optional<Schedule> existingSchedule = getActiveUserSchedule(userId);
        if (existingSchedule.isPresent()) {
            log.debug("У пользователя {} уже есть активное расписание", userId);
            return;
        }

        // Создаем расписание по умолчанию (каждый день в 12:00)
        try {
            createSchedule(userId, "daily_12");
            log.info("Создано расписание по умолчанию для пользователя {}", userId);
        } catch (Exception e) {
            log.error("Ошибка при создании расписания по умолчанию для пользователя {}: ", userId, e);
        }
    }

    /**
     * Получить количество расписаний, выполненных сегодня
     */
    public long getExecutedTodayCount() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        return scheduleRepository.countExecutedToday(startOfDay, endOfDay);
    }

    /**
     * Получить пользователей, зарегистрированных сегодня
     */
    public List<User> getUsersRegisteredToday() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        return userRepository.findByRegisteredAtBetween(startOfDay, endOfDay);
    }

    /**
     * Получить активных пользователей за сегодня
     */
    public List<User> getUsersActiveToday() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        return userRepository.findByLastActiveBetween(startOfDay, endOfDay);
    }

    /**
     * Получить статистику регистраций за последние дни
     */
    public Map<LocalDate, Long> getRegistrationStatsByDays(int daysBack) {
        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);
        Map<LocalDate, Long> stats = new HashMap<>();

        // Заполняем статистику по дням
        for (int i = daysBack; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = dayStart.plusDays(1);

            long count = userRepository.countByRegisteredAtBetween(dayStart, dayEnd);
            stats.put(date, count);
        }

        return stats;
    }
}
