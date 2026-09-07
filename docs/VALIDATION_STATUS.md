# Статус проверки

## Geo Reminder 0.2.0 — GitHub Actions PASS, device pending

7 сентября 2026 года выпущен новый debug prerelease по обратной связи о задержке
геосрабатывания и плохо заметном уведомлении.

```text
app commit: f1904eeb83f30efb4276be49e3b972a056bfca65
workflow: Android CI #20
run ID: 34148227917
tag: debug-f1904eeb83f3
package: com.onedayonemasterpiece.georeminder.debug
version: 0.2.0-debug
APK size: 6625414 bytes
APK SHA-256: ee7da66aeb102976219517b6019c48e2853c197fc9ed9b4a2c54ef3a4680de13
```

Успешно выполнены validation, `testDebugUnitTest`, `lintDebug`, `assembleDebug`,
формирование distribution bundle, Actions artifact и публикация prerelease.
Новые `LatencyMetricsTest` входят в прошедший unit-test step.

В 0.2.0 добавлены `geo-reminders-v2`, прямой переход в системные настройки его
звука, channel sound/vibration diagnostics и четыре latency-поля в журнале.
Подробности: `docs/RELEASE_0.2.0_2026-09-07.md`.

**Аппаратная приёмка 0.2.0 ещё не выполнена.** Поэтому PASS выше относится к
сборке/тестам/публикации APK, а не к доказанной слышимости выбранного звука или
реальной geofence latency на Samsung.

## Предыдущая базовая сборка 0.1.0 — GitHub Actions PASS

Проверенный app commit:

```text
afa51c3a001cff878f896914004878b8dab77e3b
```

Workflow run:

```text
Android CI #11
run ID: 33075977447
built_at: 2026-08-27T13:19:26Z
```

Успешно выполнены:

- Python validation `config/rules.example.json`;
- `testDebugUnitTest`;
- `lintDebug`;
- `assembleDebug`;
- формирование distribution bundle;
- SHA-256 APK;
- публикация Actions artifact;
- публикация debug prerelease.

Опубликованный prerelease:

```text
tag: debug-afa51c3a001c
package: com.onedayonemasterpiece.georeminder.debug
version: 0.1.0-debug
APK size: 6618406 bytes
APK SHA-256: 048f1adeab7a868dd4aa805d1f886d109161d316591812c1534f7596e62ada2e
```

Скачанный Actions artifact был распакован отдельно, фактический SHA-256 APK
совпал с опубликованным checksum; `sha256sum -c` вернул `OK`.

## Samsung Galaxy S21 Ultra provisioning — PASS для 0.1.0

Проверено 27 августа 2026 года на Samsung Galaxy S21 Ultra `SM-G998B`:

- репозиторий клонирован на implementation-ветке;
- APK скачан из prerelease `debug-afa51c3a001c`;
- ожидаемый и фактический SHA-256 совпали;
- APK установлен штатным `adb install -r` по проводному ADB;
- приложение успешно запущено;
- precise location, background location и notifications выданы;
- notification channel имеет importance `HIGH`;
- Samsung battery mode оставлен `Optimized`, exemption не включён;
- экраны «Напоминания», «Журнал» и «Диагностика» доступны;
- записи журнала сохраняются после перезапуска приложения;
- JSONL-журнал, diagnostics и релевантный logcat успешно выгружены локально.

Серийный номер устройства, локальные пути и выгруженный журнал намеренно не
публикуются в репозитории.

## Ожидаемая граница нового устройства: rules=0

На первом запуске правил и зон ещё нет:

```text
rules=0
zones=0
```

Debug-команда `TEST_NOTIFICATION` выбирает существующее правило с как минимум
одной зоной. Поэтому при `rules=0` она корректно возвращает:

```text
No matching rule with at least one zone
```

Это не ошибка установки, разрешений, notification channel или журнала. Нельзя
создавать фиктивную координату только ради зелёного smoke-test.

## Где хранится конфигурация

- `config/rules.schema.json`, `config/rules.example.json`, rule contract и skill
  версионируются в GitHub;
- фактический `config/rules.local.json` создаётся в локальной копии только после
  получения реальных напоминаний и координат и исключён из Git;
- телефон хранит применённый набор правил, registration status и audit journal.

Персональные напоминания и координаты не должны автоматически попадать в
публичный репозиторий.

## Следующая аппаратная проверка 0.2.0

- установить последний prerelease штатным `scripts/install-latest-release.ps1`;
- подтвердить `version=0.2.0-debug` и `notification_channel_id=geo-reminders-v2`;
- выбрать различимый системный Samsung sound через кнопку в Diagnostics;
- выполнить test notification для существующего реального правила;
- подтвердить звук пользователем;
- снять diagnostics/JSONL;
- провести физический `ENTER` в известную зону с observation mark;
- сопоставить `radius_m`, `responsiveness_ms`, `triggeringLocation.time_ms` и
  новые latency-поля;
- только после измерения решать, менять ли radius/`responsiveness_ms`;
- отдельно проверить reboot recovery и battery behavior.

Все аппаратные проверки выполняются из готового prerelease без локальной
Android-сборки.
