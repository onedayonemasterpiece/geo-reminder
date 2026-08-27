# Geo Reminder

Локальное Android-приложение для геоконтекстных напоминаний с постоянным
проверяемым журналом. Первая цель — испытание на Samsung Galaxy S21 Ultra без
сервера: OpenCode готовит конечный список конкретных координат, приложение
получает его по проводному ADB, регистрирует круговые геозоны и дальше работает
автономно.

## Что реализовано

- экран **«Напоминания»** со всеми текущими правилами, зонами, координатами,
  режимом повторения и runtime-состоянием;
- экран **«Журнал»** с историей импортов, регистраций, геособытий, решений по
  правилу и доставок уведомлений;
- экран **«Диагностика»** с разрешениями, состоянием notification channel,
  ограничениями фоновой работы, SHA правил и последней регистрацией;
- постоянная SQLite/WAL-база, сохраняющаяся после перезапуска процесса,
  перезагрузки телефона и обновления APK;
- JSONL-экспорт полного журнала;
- состояния доставки `ATTEMPTED`, `FAILED`, `POSTED_UNCONFIRMED`,
  `ACTIVE_CONFIRMED`, `TAPPED`, `DISMISSED` и
  `NO_LONGER_ACTIVE_UNKNOWN`;
- восстановление геозон после перезагрузки;
- debug-only ADB-команды импорта, тестового уведомления, диагностики,
  перерегистрации, контрольной отметки и экспорта;
- строгий Python/Android validator `rules.json`;
- один GitHub Actions workflow для tests, lint, APK, SHA-256, artifact и debug
  prerelease.

## Что означает журнал доставки

Журнал не маскирует разные этапы одним словом «показано»:

1. `GEOFENCE_EVENT_RECEIVED` — Google Play services передал событие приложению.
2. `REMINDER_TRIGGER_ACCEPTED` или `...SKIPPED` — локальное правило принято либо
   отклонено с причиной.
3. `NOTIFICATION_ATTEMPT` — приложение начало доставку.
4. `ACTIVE_CONFIRMED` — ID найден в
   `NotificationManager.activeNotifications`.
5. `TAPPED` или `DISMISSED` — зафиксировано действие с уведомлением.
6. `NO_LONGER_ACTIVE_UNKNOWN` — уведомление исчезло без надёжно наблюдаемого
   нажатия или смахивания.

`ACTIVE_CONFIRMED` подтверждает наличие уведомления в системном списке Android,
но не доказывает, что человек заметил баннер или услышал звук. Подробная
интерпретация: `docs/JOURNAL_AND_DEBUGGING.md`.

## Граница MVP

В первой версии намеренно отсутствуют сервер, MCP-транспорт к телефону, FCM,
PWA, аккаунты, foreground location service, периодический GPS, polling и
категории вроде «любой супермаркет». Поддерживается только конечный проверенный
список кругов `latitude + longitude + radius_m`, максимум 100 активных зон.

ADB нужен при установке, обновлении правил и снятии диагностики. После
отключения USB срабатывание выполняется Android Geofencing API и локальным
уведомлением; OpenCode и компьютер не нужны.

## Готовый APK

GitHub Actions является build authority. Устанавливать JDK, Gradle, Android SDK
или Android Studio на локальный компьютер для обычного развёртывания не нужно.

Текущий проверенный prerelease:

```text
tag: debug-afa51c3a001c
app commit: afa51c3a001cff878f896914004878b8dab77e3b
package: com.onedayonemasterpiece.georeminder.debug
version: 0.1.0-debug
SHA-256: 048f1adeab7a868dd4aa805d1f886d109161d316591812c1534f7596e62ada2e
```

Каждый успешный implementation build публикует стабильные assets:

- `geo-reminder-debug.apk`;
- `geo-reminder-debug.apk.sha256`;
- `build-info.json`.

## Установка на телефон

Локально нужны только Git/GitHub CLI, ADB platform-tools и подключённый телефон.
Скрипты сами выбирают последний non-draft debug prerelease, скачивают assets,
проверяют SHA-256 и выполняют `adb install -r`. Они не удаляют приложение и не
очищают журнал.

Windows PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/install-latest-release.ps1
```

Bash/Git Bash/WSL:

```bash
./scripts/install-debug.sh
```

Также можно передать Bash-скрипту явный APK, рядом с которым лежит файл
`<apk>.sha256`:

```bash
./scripts/install-debug.sh /path/to/geo-reminder-debug.apk
```

После установки пользователь вручную выдаёт:

1. точную геолокацию;
2. доступ к местоположению **«Разрешить всегда»**;
3. разрешение уведомлений.

Начинать тест следует в стандартном Samsung-режиме батареи `Optimized`.

## Проверка до реальных геозон

```bash
./scripts/test-notification.sh
./scripts/diagnose.sh
./scripts/export-journal.sh
```

Нужно увидеть попытку уведомления и её результат в постоянном журнале. Это
проверка notification plumbing, а не физический geofence test.

## Подготовка правил в OpenCode

```bash
cp config/rules.example.json config/rules.local.json
# заменить отключённые примеры фактическим конечным списком точек
python3 scripts/validate-rules.py config/rules.local.json
./scripts/import-rules.sh config/rules.local.json
```

Пример содержит только `enabled: false`: случайный импорт не должен создать
активные геозоны. Канонический контракт находится в
`config/rules.schema.json` и
`.opencode/skills/location-reminder-adb/references/rule-contract.md`.

## Физическая проверка

```bash
./scripts/mark-observation.sh "вышел из тестовой зоны"
```

Отключите USB, начните явно снаружи круга, пересеките границу и запишите реальное
время. Затем подключитесь снова и выполните:

```bash
./scripts/diagnose.sh
./scripts/export-journal.sh
```

Если напоминание не появилось:

- нет `GEOFENCE_EVENT_RECEIVED` — событие не было доставлено location stack;
- событие есть, но присутствует `...SKIPPED` — причина в правиле, cooldown или
  завершённом `once`;
- есть `NOTIFICATION_BLOCKED/FAILED` — проблема разрешения или канала;
- есть `ACTIVE_CONFIRMED`, но человек ничего не заметил — проверяются канал,
  звук, DND и Samsung UI;
- есть `NO_LONGER_ACTIVE_UNKNOWN` — уведомление исчезло без наблюдаемого
  действия; время и состояние останутся в экспорте.

## Хранение

Журнал не очищается автоматически. Он переживает перезагрузку и обновление APK.
Очистка данных приложения или удаление приложения уничтожает локальную БД,
поэтому provisioning scripts никогда не выполняют `adb uninstall` или
`pm clear`. Перед ручными разрушительными действиями сначала экспортируйте
журнал.

## Разработка приложения

Локальные build scripts сохранены для разработчика репозитория, но не входят в
OpenCode provisioning workflow. Исправления приложения делаются через
ChatGPT + GitHub, после чего новый APK снова выпускается GitHub Actions.

## Репозиторные инструкции

- `AGENTS.md` — границы и порядок работы для coding-agent;
- `.opencode/skills/location-reminder-adb/SKILL.md` — project skill;
- `START_HERE_FOR_OPENCODE.md` — первое практическое задание;
- `docs/SAMSUNG_S21_ULTRA_TEST.md` — аппаратный протокол;
- `docs/ARCHITECTURE.md` — устройство runtime;
- `docs/VALIDATION_STATUS.md` — фактически выполненные проверки.
