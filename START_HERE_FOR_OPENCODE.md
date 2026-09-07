# START HERE FOR OPENCODE — Geo Reminder

## Цель текущей локальной сессии

Разработка приложения выполнена в ChatGPT + GitHub. Локальный OpenCode нужен
только для provisioning и аппаратной проверки на Samsung по проводному ADB.

Не устанавливай Android build tooling, не собирай APK локально и не редактируй
Kotlin/Gradle/Actions. Если обнаружен runtime-дефект — сохрани доказательства и
верни их в GitHub для следующей реализации.

## Актуальная сборка

```text
version: 0.2.0-debug
commit: f1904eeb83f30efb4276be49e3b972a056bfca65
tag: debug-f1904eeb83f3
package: com.onedayonemasterpiece.georeminder.debug
APK SHA-256: ee7da66aeb102976219517b6019c48e2853c197fc9ed9b4a2c54ef3a4680de13
```

Release:
https://github.com/onedayonemasterpiece/geo-reminder/releases/tag/debug-f1904eeb83f3

## Что проверяет 0.2.0

Эта версия разделяет два эффекта:

1. задержку доставки geofence event;
2. заметность уже опубликованного notification.

В ней есть новый канал `geo-reminders-v2`, кнопка прямого перехода в его
системные настройки и latency-поля в журнале:

- `triggering_location_to_receiver_ms`;
- `geofence_receiver_to_notification_attempt_ms`;
- `notification_attempt_to_posted_ms`;
- `geofence_receiver_to_notification_posted_ms`.

## Обязательный порядок

1. Прочитай `.opencode/skills/location-reminder-adb/SKILL.md`.
2. Убедись, что подключён ровно один authorized ADB device, либо явно задай
   `DEVICE_SERIAL`.
3. Установи актуальный prerelease штатным скриптом:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/install-latest-release.ps1
```

Он автоматически выбирает последний опубликованный `debug-*` prerelease,
скачивает APK + checksum, проверяет SHA-256 и выполняет `adb install -r`, не
очищая данные и журнал.

4. Запусти приложение и проверь в **Диагностике**:

```text
version=0.2.0-debug
notification_channel_id=geo-reminders-v2
notification_channel=HIGH
```

5. Нажми **«Настроить звук геонапоминаний»**. Пользователь выбирает любой
   различимый системный Samsung notification sound. Не обходи системный UI и не
   меняй DND/громкость скрыто.
6. Выполни test notification для существующего реального правила:

```bash
./scripts/test-notification.sh [rule-id]
```

Пользователь должен подтвердить, что услышал выбранный звук и узнаёт его как
Geo Reminder.
7. Выполни:

```bash
./scripts/diagnose.sh
./scripts/export-journal.sh
```

Сохрани локально вывод и убедись, что diagnostics показывает фактический sound
URI/presence и vibration state.
8. Перед физическим проходом запиши observation mark:

```bash
./scripts/mark-observation.sh "0.2.0 physical geofence test started outside"
```

9. Отключи USB. Пользователь начинает явно снаружи одной известной зоны,
   пересекает её границу и фиксирует наблюдаемое время отдельно от системного
   timestamp.
10. После прохода подключи USB и снова экспортируй diagnostics/journal.
11. Для соответствующего geofence event выпиши:

```text
rule id / zone id
radius_m
responsiveness_ms
triggeringLocation.time_ms
triggering_location_to_receiver_ms
geofence_receiver_to_notification_attempt_ms
notification_attempt_to_posted_ms
geofence_receiver_to_notification_posted_ms
субъективное время, когда пользователь услышал звук
```

12. Не выдавай `triggeringLocation.time_ms` за точный момент физического входа.
   Это системный location sample, а не ground truth границы.
13. Только по результату измерения предлагай изменение radius или
   `responsiveness_ms`. Не компенсируй задержку continuous GPS, foreground
   location service или polling.

## Про собственный аудиофайл

В 0.2.0 отдельный bundled-файл не нужен: системный звук выбирается пользователем
в настройках channel и не требует новой сборки.

Если владелец позже хочет одинаковый фирменный звук на всех устройствах, не
добавляй его локально в эту provisioning-сессию. Передай файл/путь как вход для
следующей GitHub-разработки: аудио должно попасть в Android resources, после чего
нужен новый versioned notification channel (например `geo-reminders-v3`).
Существующий `v2` нельзя считать программно перенастраиваемым после создания.

## Стоп-условия

Остановись и верни доказательства, если:

- checksum не совпал;
- `adb install -r` не проходит;
- версия после установки не `0.2.0-debug`;
- кнопка channel settings не открывает `geo-reminders-v2`;
- test notification не создаётся при существующем валидном правиле;
- новый geofence event есть, а notification path завершается ошибкой;
- diagnostics или JSONL export ломаются.

Не удаляй приложение, не выполняй `pm clear`, не включай wireless ADB и не
стирай существующий журнал.
