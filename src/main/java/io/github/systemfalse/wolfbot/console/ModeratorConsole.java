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

package io.github.systemfalse.wolfbot.console;

import io.github.systemfalse.wolfbot.model.Moderator;
import io.github.systemfalse.wolfbot.service.ModeratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Scanner;

@Component
@RequiredArgsConstructor
@Slf4j
public class ModeratorConsole implements CommandLineRunner {

    private final ModeratorService moderatorService;
    private final ApplicationContext applicationContext;

    public static void main(String[] args) {
        System.setProperty("spring.main.web-application-type", "none");
        System.setProperty("telegram.bot.enabled", "false");
        SpringApplication.run(ModeratorConsole.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // Проверяем, запущена ли консоль для управления модераторами
        if (args.length > 0 && "moderator-console".equals(args[0])) {
            runModeratorConsole();
        }
    }

    /**
     * Запуск интерактивной консоли управления модераторами
     */
    public void runModeratorConsole() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=================================");
        System.out.println("🐺 Wolf Bot - Консоль модераторов");
        System.out.println("=================================");
        System.out.println();

        while (true) {
            printMenu();
            System.out.print("Выберите действие: ");

            String choice = scanner.nextLine().trim();

            try {
                switch (choice) {
                    case "1" -> listModerators();
                    case "2" -> addModerator(scanner);
                    case "3" -> removeModerator(scanner);
                    case "4" -> activateModerator(scanner);
                    case "5" -> deactivateModerator(scanner);
                    case "6" -> showModeratorStats(scanner);
                    case "7" -> exportModerators();
                    case "0" -> {
                        System.out.println("Выход из консоли...");
                        return;
                    }
                    default -> System.out.println("❌ Неверный выбор. Попробуйте еще раз.");
                }
            } catch (Exception e) {
                System.err.println("❌ Ошибка: " + e.getMessage());
                log.error("Ошибка в консоли модераторов: ", e);
            }

            System.out.println();
        }
    }

    /**
     * Печать меню
     */
    private void printMenu() {
        System.out.println("📋 Доступные команды:");
        System.out.println("1. Список модераторов");
        System.out.println("2. Добавить модератора");
        System.out.println("3. Удалить модератора");
        System.out.println("4. Активировать модератора");
        System.out.println("5. Деактивировать модератора");
        System.out.println("6. Статистика модератора");
        System.out.println("7. Экспорт списка модераторов");
        System.out.println("0. Выход");
        System.out.println();
    }

    /**
     * Показать список модераторов
     */
    private void listModerators() {
        List<Moderator> moderators = moderatorService.getAllModerators();

        if (moderators.isEmpty()) {
            System.out.println("📝 Модераторы не найдены.");
            return;
        }

        System.out.println("👥 Список модераторов:");
        System.out.println("─".repeat(80));
        System.out.printf("%-5s %-12s %-20s %-15s %-10s %-12s%n",
                "ID", "Telegram ID", "Имя", "Username", "Статус", "Модераций");
        System.out.println("─".repeat(80));

        for (Moderator moderator : moderators) {
            System.out.printf("%-5d %-12d %-20s %-15s %-10s %-12d%n",
                    moderator.getId(),
                    moderator.getTelegramId(),
                    truncate(moderator.getDisplayName(), 20),
                    truncate(moderator.getUsername() != null ? "@" + moderator.getUsername() : "-", 15),
                    moderator.getActive() ? "Активен" : "Неактивен",
                    moderator.getModerationCount());
        }

        System.out.println("─".repeat(80));
        System.out.printf("Всего модераторов: %d (активных: %d)%n",
                moderators.size(),
                moderators.stream().mapToInt(m -> m.getActive() ? 1 : 0).sum());
    }

    /**
     * Добавить нового модератора
     */
    private void addModerator(Scanner scanner) {
        System.out.println("➕ Добавление нового модератора");
        System.out.println();

        System.out.print("Telegram ID: ");
        String telegramIdStr = scanner.nextLine().trim();

        long telegramId;
        try {
            telegramId = Long.parseLong(telegramIdStr);
        } catch (NumberFormatException e) {
            System.out.println("❌ Неверный формат Telegram ID. Введите число.");
            return;
        }

        // Проверяем, существует ли уже модератор с таким ID
        if (moderatorService.findByTelegramId(telegramId).isPresent()) {
            System.out.println("❌ Модератор с таким Telegram ID уже существует.");
            return;
        }

        System.out.print("Username (без @, опционально): ");
        String username = scanner.nextLine().trim();
        if (username.isEmpty()) {
            username = null;
        }

        System.out.print("Имя (опционально): ");
        String firstName = scanner.nextLine().trim();
        if (firstName.isEmpty()) {
            firstName = null;
        }

        try {
            Moderator moderator = moderatorService.addModerator(telegramId, username, firstName);
            System.out.printf("✅ Модератор успешно добавлен! ID: %d%n", moderator.getId());
            System.out.printf("📧 Уведомите пользователя с Telegram ID %d о назначении модератором.%n", telegramId);
        } catch (Exception e) {
            System.out.println("❌ Ошибка при добавлении модератора: " + e.getMessage());
        }
    }

    /**
     * Удалить модератора
     */
    private void removeModerator(Scanner scanner) {
        System.out.println("➖ Удаление модератора");
        System.out.println();

        System.out.print("Введите Telegram ID модератора для удаления: ");
        String telegramIdStr = scanner.nextLine().trim();

        long telegramId;
        try {
            telegramId = Long.parseLong(telegramIdStr);
        } catch (NumberFormatException e) {
            System.out.println("❌ Неверный формат Telegram ID. Введите число.");
            return;
        }

        var moderatorOpt = moderatorService.findByTelegramId(telegramId);
        if (moderatorOpt.isEmpty()) {
            System.out.println("❌ Модератор с таким Telegram ID не найден.");
            return;
        }

        Moderator moderator = moderatorOpt.get();
        System.out.printf("Модератор: %s (ID: %d)%n", moderator.getDisplayName(), moderator.getId());
        System.out.printf("Выполнено модераций изображений: %d%n", moderator.getModerationCount());
        System.out.println();

        System.out.print("Вы уверены, что хотите удалить этого модератора? (yes/no): ");
        String confirmation = scanner.nextLine().trim().toLowerCase();

        if ("yes".equals(confirmation) || "y".equals(confirmation) || "да".equals(confirmation)) {
            try {
                moderatorService.removeModerator(telegramId);
                System.out.println("✅ Модератор успешно удален.");
            } catch (Exception e) {
                System.out.println("❌ Ошибка при удалении модератора: " + e.getMessage());
            }
        } else {
            System.out.println("❌ Удаление отменено.");
        }
    }

    /**
     * Активировать модератора
     */
    private void activateModerator(Scanner scanner) {
        System.out.println("🔓 Активация модератора");
        System.out.println();

        System.out.print("Введите Telegram ID модератора: ");
        String telegramIdStr = scanner.nextLine().trim();

        long telegramId;
        try {
            telegramId = Long.parseLong(telegramIdStr);
        } catch (NumberFormatException e) {
            System.out.println("❌ Неверный формат Telegram ID. Введите число.");
            return;
        }

        try {
            boolean result = moderatorService.setModeratorActive(telegramId, true);
            if (result) {
                System.out.println("✅ Модератор успешно активирован.");
            } else {
                System.out.println("❌ Модератор не найден.");
            }
        } catch (Exception e) {
            System.out.println("❌ Ошибка при активации модератора: " + e.getMessage());
        }
    }

    /**
     * Деактивировать модератора
     */
    private void deactivateModerator(Scanner scanner) {
        System.out.println("🔒 Деактивация модератора");
        System.out.println();

        System.out.print("Введите Telegram ID модератора: ");
        String telegramIdStr = scanner.nextLine().trim();

        long telegramId;
        try {
            telegramId = Long.parseLong(telegramIdStr);
        } catch (NumberFormatException e) {
            System.out.println("❌ Неверный формат Telegram ID. Введите число.");
            return;
        }

        try {
            boolean result = moderatorService.setModeratorActive(telegramId, false);
            if (result) {
                System.out.println("✅ Модератор успешно деактивирован.");
            } else {
                System.out.println("❌ Модератор не найден.");
            }
        } catch (Exception e) {
            System.out.println("❌ Ошибка при деактивации модератора: " + e.getMessage());
        }
    }

    /**
     * Показать статистику модератора
     */
    private void showModeratorStats(Scanner scanner) {
        System.out.println("📊 Статистика модератора");
        System.out.println();

        System.out.print("Введите Telegram ID модератора: ");
        String telegramIdStr = scanner.nextLine().trim();

        long telegramId;
        try {
            telegramId = Long.parseLong(telegramIdStr);
        } catch (NumberFormatException e) {
            System.out.println("❌ Неверный формат Telegram ID. Введите число.");
            return;
        }

        var moderatorOpt = moderatorService.findByTelegramId(telegramId);
        if (moderatorOpt.isEmpty()) {
            System.out.println("❌ Модератор с таким Telegram ID не найден.");
            return;
        }

        Moderator moderator = moderatorOpt.get();
        var stats = moderatorService.getModeratorStats(telegramId);

        System.out.println("─".repeat(50));
        System.out.printf("👤 Модератор: %s%n", moderator.getDisplayName());
        System.out.printf("🆔 Telegram ID: %d%n", moderator.getTelegramId());
        System.out.printf("👤 Username: %s%n", moderator.getUsername() != null ? "@" + moderator.getUsername() : "Не указан");
        System.out.printf("📅 Добавлен: %s%n", moderator.getAddedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
        System.out.printf("🔘 Статус: %s%n", moderator.getActive() ? "Активен" : "Неактивен");
        System.out.println("─".repeat(50));
        System.out.printf("📊 Всего модераций: %d%n", stats.totalModerations());
        System.out.printf("✅ Одобрено: %d%n", stats.approvedCount());
        System.out.printf("❌ Отклонено: %d%n", stats.rejectedCount());
        System.out.printf("🚫 Заблокировано: %d%n", stats.blockedCount());
        System.out.printf("📅 Модераций за последние 30 дней: %d%n", stats.moderationsLastMonth());
        System.out.printf("📅 Модераций за сегодня: %d%n", stats.moderationsToday());
        System.out.println("─".repeat(50));
    }

    /**
     * Экспорт списка модераторов
     */
    private void exportModerators() {
        try {
            String filename = moderatorService.exportModerators();
            System.out.printf("✅ Список модераторов экспортирован в файл: %s%n", filename);
        } catch (Exception e) {
            System.out.println("❌ Ошибка при экспорте: " + e.getMessage());
        }
    }

    /**
     * Обрезать строку до указанной длины
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return "-";
        return str.length() > maxLength ? str.substring(0, maxLength - 3) + "..." : str;
    }
}
