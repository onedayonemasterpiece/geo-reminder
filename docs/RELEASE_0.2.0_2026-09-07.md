# Geo Reminder 0.2.0 — release record

Дата: 7 сентября 2026.

## Выпущенная сборка

```text
commit: f1904eeb83f30efb4276be49e3b972a056bfca65
workflow: Android CI #20
run ID: 34148227917
tag: debug-f1904eeb83f3
package: com.onedayonemasterpiece.georeminder.debug
version: 0.2.0-debug
APK size: 6625414 bytes
APK SHA-256: ee7da66aeb102976219517b6019c48e2853c197fc9ed9b4a2c54ef3a4680de13
```

Release:
https://github.com/onedayonemasterpiece/geo-reminder/releases/tag/debug-f1904eeb83f3

APK:
https://github.com/onedayonemasterpiece/geo-reminder/releases/download/debug-f1904eeb83f3/geo-reminder-debug.apk

## GitHub Actions — PASS

Финальный workflow успешно выполнил:

- validation `config/rules.example.json`;
- `testDebugUnitTest`, включая новые tests `LatencyMetricsTest`;
- `lintDebug`;
- `assembleDebug`;
- SHA-256 distribution;
- Actions artifact;
- immutable debug prerelease publication.

Это подтверждает сборку и статические/модульные проверки. Телефонная приёмка
версии 0.2.0 ещё не выполнена.

## Что изменилось в 0.2.0

- новый versioned notification channel `geo-reminders-v2`;
- channel создаётся как `IMPORTANCE_HIGH`, с vibration и системным default
  notification sound;
- в экране диагностики появилась кнопка **«Настроить звук геонапоминаний»**,
  открывающая системные настройки именно этого канала;
- diagnostics выводит channel ID, importance, sound URI, vibration,
  `canBypassDnd`, audio usage и наличие legacy channel;
- diagnostics показывает `responsiveness_ms` активных правил;
- журнал получает вычислимые поля:
  - `triggering_location_to_receiver_ms`;
  - `geofence_receiver_to_notification_attempt_ms`;
  - `notification_attempt_to_posted_ms`;
  - `geofence_receiver_to_notification_posted_ms`;
- `GEOFENCE_ZONE_MATCHED` пишет radius и `responsiveness_ms`;
- versionCode поднят до `2`, versionName до `0.2.0`.

## Как выбрать звук

В этот APK не встроена отдельная аудиозапись. При первом создании `v2` Android
назначает текущий системный notification sound. После установки откройте в
Geo Reminder **Диагностика → Настроить звук геонапоминаний** и выберите любой
доступный системный звук Samsung. Выбор принадлежит notification channel и
сохраняется Android; новая сборка для смены системного звука не нужна.

Если нужен одинаковый фирменный звук на разных устройствах, следует добавить
аудиофайл как bundled Android resource и выпустить следующий versioned channel
(например, `geo-reminders-v3`). Не менять sound существующего `v2` в коде: после
создания auditory behavior канала контролируется Android/пользователем.

## Установка на Samsung

Существующий provisioning script автоматически выбирает последний опубликованный
`debug-*` prerelease, то есть сейчас 0.2.0:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/install-latest-release.ps1
```

Скрипт скачивает APK и checksum, сверяет SHA-256 и использует `adb install -r`,
не удаляя приложение и не очищая журнал.

## Аппаратная приёмка 0.2.0

После установки:

1. открыть **Диагностика** и проверить `version=0.2.0-debug` и
   `notification_channel_id=geo-reminders-v2`;
2. открыть настройки звука канала и выбрать различимый системный звук;
3. выполнить `scripts/test-notification.sh [rule-id]` и подтвердить звук;
4. экспортировать diagnostics/journal;
5. провести реальный вход в одну геозону и сравнить новые latency-поля;
6. только после измерения решать, требуется ли менять radius или
   `responsiveness_ms`.

Не считать системный `triggeringLocation.time_ms` точным моментом физического
пересечения границы: пользовательское наблюдение и системный timestamp остаются
разными источниками.
