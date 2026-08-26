# Geo Reminder

Локальное Android-приложение для геоконтекстных напоминаний с постоянным
проверяемым журналом. Первая цель — испытание на Samsung Galaxy S21 Ultra без
сервера: OpenCode готовит конечный список конкретных координат, приложение
получает его по проводному ADB, регистрирует круговые геозоны и дальше работает
автономно.

## Что уже реализовано

- экран **«Напоминания»** со всеми текущими правилами, зонами, координатами,
  режимом повторения и фактическим runtime-состоянием;
- экран **«Журнал»** с историей импортов, регистраций, системных геособытий,
  решений по правилу и доставок уведомлений;
- экран **«Диагностика»** с разрешениями, состоянием канала уведомлений,
  ограничениями фоновой работы, хешем правил и последней регистрацией;
- постоянная SQLite/WAL-база, сохраняющаяся после перезапуска процесса,
  перезагрузки телефона и обновления APK;
- JSONL-экспорт полного журнала с телефона или по ADB;
- отдельные состояния доставки: `ATTEMPTED`, `FAILED`,
  `POSTED_UNCONFIRMED`, `ACTIVE_CONFIRMED`, `TAPPED`, `DISMISSED` и
  `NO_LONGER_ACTIVE_UNKNOWN`;
- запись одноразового `triggeringLocation` только при фактическом геособытии —
  непрерывной истории перемещений нет;
- восстановление геозон после перезагрузки;
- debug-only ADB-команды импорта, тестового уведомления, диагностики,
  перерегистрации, контрольной отметки и экспорта;
- строгий локальный и Android-валидатор `rules.json`;
- GitHub Actions: unit tests, Android lint, debug APK и SHA-256 как artifact.

## Что означает журнал доставки

Журнал не маскирует разные этапы одним словом «показано»:

1. `GEOFENCE_EVENT_RECEIVED` — Google Play services передал событие приложению.
2. `REMINDER_TRIGGER_ACCEPTED` или `...SKIPPED` — локальное правило принято либо
   отклонено с причиной.
3. `NOTIFICATION_ATTEMPT` — приложение начало доставку.
4. `ACTIVE_CONFIRMED` — `notify()` завершился и ID найден в
   `NotificationManager.activeNotifications`.
5. `TAPPED` или `DISMISSED` — зафиксировано действие с уведомлением.
6. `NO_LONGER_ACTIVE_UNKNOWN` — ранее активное уведомление исчезло, но приложение
   не получило надёжного события нажатия или смахивания.

`ACTIVE_CONFIRMED` подтверждает наличие уведомления в системном списке Android,
но не может доказать, что человек физически заметил баннер или услышал звук.
Именно поэтому эти состояния не объединены.

Подробная интерпретация: `docs/JOURNAL_AND_DEBUGGING.md`.

## Граница MVP

В первой версии намеренно отсутствуют сервер, MCP-транспорт к телефону, FCM,
PWA, аккаунты, foreground location service, периодический GPS, polling и
категории вроде «любой супермаркет». Поддерживается только конечный проверенный
список кругов `latitude + longitude + radius_m`, максимум 100 активных зон.

ADB нужен при установке, обновлении правил и снятии диагностики. После
отключения USB срабатывание выполняется Android Geofencing API и локальным
уведомлением; OpenCode и компьютер не нужны.

## Сборка

### GitHub Actions

Workflow `.github/workflows/android-ci.yml` собирает debug APK на каждом push в
`main`/`feat/**` и в pull request. Artifact содержит:

- `app-debug.apk`;
- `app-debug.apk.sha256`;
- lint report;
- unit-test report.

### Локально

Требуются JDK 17, Android SDK 36, build-tools 36.0.0, Python 3 и ADB.

```bash
./scripts/build-debug.sh
./scripts/install-debug.sh
```

Если `gradlew` ещё не создан, `scripts/gradle.sh` скачает официальный Gradle
8.13 и проверит SHA-256 архива перед запуском. `scripts/bootstrap-gradle.sh`
может дополнительно создать обычный wrapper.

## Подготовка правил в OpenCode

```bash
cp config/rules.example.json config/rules.local.json
# заменить отключённые примеры фактическим конечным списком точек
python3 scripts/validate-rules.py config/rules.local.json
./scripts/import-rules.sh config/rules.local.json
```

Пример намеренно содержит только `enabled: false`: случайный импорт не должен
создать активные геозоны. Канонический контракт находится в
`config/rules.schema.json` и
`.opencode/skills/location-reminder-adb/references/rule-contract.md`.

## Разрешения на Samsung

После установки пользователь вручную выдаёт:

1. точную геолокацию;
2. доступ к местоположению **«Разрешить всегда»** через системные настройки;
3. разрешение на уведомления.

Начинать тест следует в стандартном Samsung-режиме батареи `Optimized`. Только
после подтверждённых пропусков проверяется `Deep sleeping apps`; постоянный
foreground service не добавляется.

## Диагностика и журнал

```bash
./scripts/test-notification.sh [rule-id]
./scripts/mark-observation.sh "вышел из тестовой зоны"
./scripts/reregister.sh
./scripts/diagnose.sh
./scripts/export-journal.sh
```

Физический тест начинается явно снаружи круга. При регистрации задан
`setInitialTrigger(0)`, поэтому само добавление зоны не должно имитировать
реальный вход.

Если напоминание не появилось:

- нет `GEOFENCE_EVENT_RECEIVED` — событие не было доставлено location stack;
- событие есть, но присутствует `...SKIPPED` — причина в правиле, cooldown или
  завершённом `once`;
- есть `NOTIFICATION_BLOCKED/FAILED` — проблема разрешения или канала;
- есть `ACTIVE_CONFIRMED`, но человек ничего не заметил — уведомление было в
  системном списке, дальше проверяются канал, звук, DND и Samsung UI;
- есть `NO_LONGER_ACTIVE_UNKNOWN` — уведомление исчезло без наблюдаемого
  действия; время и состояние останутся в экспорте.

## Хранение

Журнал не очищается автоматически. Он переживает перезагрузку и обновление APK.
Очистка данных приложения или удаление приложения уничтожает локальную БД,
поэтому перед такими действиями выполните экспорт. Cloud backup отключён.

## Репозиторные инструкции

- `AGENTS.md` — границы и порядок работы для coding-agent;
- `.opencode/skills/location-reminder-adb/SKILL.md` — project skill;
- `START_HERE_FOR_OPENCODE.md` — первое практическое задание;
- `docs/SAMSUNG_S21_ULTRA_TEST.md` — аппаратный протокол;
- `docs/ARCHITECTURE.md` — устройство runtime;
- `docs/VALIDATION_STATUS.md` — фактически выполненные проверки.
