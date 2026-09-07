# Samsung Galaxy S21 Ultra — аппаратная проверка Geo Reminder

## Базовые ограничения

- устройство: Samsung Galaxy S21 Ultra;
- проводной ADB;
- штатный Samsung battery mode `Optimized` в начале эксперимента;
- никаких `adb root`, wireless ADB, bootloader изменений, `pm clear` или
  `adb uninstall`;
- приложение должно продолжать работать после отключения USB;
- development APK берётся только из опубликованного GitHub prerelease и
  устанавливается через `adb install -r`.

## Актуальная версия для проверки

```text
version: 0.2.0-debug
commit: f1904eeb83f30efb4276be49e3b972a056bfca65
tag: debug-f1904eeb83f3
APK SHA-256: ee7da66aeb102976219517b6019c48e2853c197fc9ed9b4a2c54ef3a4680de13
```

GitHub Actions `Android CI #20`, run `34148227917` — PASS.

## Установка

Windows:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/install-latest-release.ps1
```

Bash/Git Bash/WSL:

```bash
./scripts/install-debug.sh
```

После установки вручную подтвердить precise location, background location
**Allow all the time** и notifications. Журнал предыдущей версии должен
сохраниться.

## Проверка нового notification channel

Открыть **Диагностика** и подтвердить:

```text
version=0.2.0-debug
notification_channel_id=geo-reminders-v2
notification_channel=HIGH
```

Проверить также:

- `notification_channel_sound` не `SILENT_OR_MISSING`;
- `notification_channel_vibration=true` либо явно зафиксировать пользовательское
  изменение;
- `notification_channel_bypass_dnd` только наблюдать, не включать обход DND;
- `responsiveness_ms` содержит фактические значения активных правил.

Нажать **«Настроить звук геонапоминаний»** и выбрать различимый системный
Samsung notification sound. Название выбранного звука зафиксировать в локальном
тестовом отчёте, если UI его показывает. Новая сборка для смены системного звука
не требуется.

## Test notification

Для существующего реального правила:

```bash
./scripts/test-notification.sh [rule-id]
./scripts/diagnose.sh
./scripts/export-journal.sh
```

Проверить:

- notification опубликован через `geo-reminders-v2`;
- пользователь действительно услышал сигнал;
- пользователь может по сигналу отличить Geo Reminder от обычного потока;
- tapping/dismissal продолжают попадать в журнал;
- отключение звука в системных настройках канала не обходится приложением.

## Физическая проверка latency

До движения:

```bash
./scripts/mark-observation.sh "0.2.0 physical test started outside"
```

Начать заведомо снаружи тестового круга. Отключить USB. Пересечь границу и
отдельно записать наблюдаемое человеком время входа/появления/звука.

После теста:

```bash
./scripts/diagnose.sh
./scripts/export-journal.sh
```

Для события выписать:

```text
rule_id
zone_id
radius_m
responsiveness_ms
triggeringLocation.time_ms
triggering_location_to_receiver_ms
geofence_receiver_to_notification_attempt_ms
notification_attempt_to_posted_ms
geofence_receiver_to_notification_posted_ms
user-observed boundary time
user-observed sound time
```

Интерпретация:

- `triggeringLocation.time_ms` — время системного location sample, не доказанный
  момент пересечения границы;
- `triggering_location_to_receiver_ms` — сколько прошло от системного sample до
  получения BroadcastReceiver;
- `geofence_receiver_to_notification_attempt_ms` и
  `notification_attempt_to_posted_ms` локализуют app-side часть;
- разница между пользовательским наблюдением и системными timestamps анализируется
  отдельно и не смешивается с app-side latency.

Если `GEOFENCE_EVENT_RECEIVED` отсутствует после observation mark, проблема до
application code. Если event есть, но app-side latency мала, звук или
notification UI не должны использоваться как объяснение задержки geofence.

## Решения после измерения

Только после одного или нескольких воспроизводимых маршрутов рассматривать:

- изменение radius;
- изменение `responsiveness_ms`;
- настройки Wi-Fi/location/battery.

Не переходить к continuous GPS, foreground location service или polling только
ради попытки получить «мгновенный» geofence.

## Bundled sound

Если системного Samsung-звука недостаточно и нужен один и тот же фирменный звук
на разных устройствах, аппаратная сессия лишь фиксирует аудиофайл как входной
артефакт. Android source не редактируется локально. Файл добавляется через
следующую GitHub-разработку, а звук получает новый versioned notification channel,
чтобы не пытаться изменить auditory behavior уже созданного `v2`.
