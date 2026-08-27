# Статус проверки

## GitHub Actions — PASS

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

## Samsung Galaxy S21 Ultra provisioning — PASS

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

В текущем APK debug-команда `TEST_NOTIFICATION` выбирает существующее правило с
как минимум одной зоной. Поэтому при `rules=0` она корректно возвращает:

```text
No matching rule with at least one zone
```

Это не ошибка установки, разрешений, notification channel или журнала. Нельзя
создавать фиктивную координату только ради зелёного smoke-test. Следующий
продуктовый шаг — сформировать реальный пользовательский
`config/rules.local.json`, импортировать его, сверить SHA желаемого и
применённого состояния и затем повторить notification smoke-test.

## Где хранится конфигурация

- `config/rules.schema.json`, `config/rules.example.json`, rule contract и skill
  версионируются в GitHub;
- фактический `config/rules.local.json` создаётся в локальной копии только после
  получения реальных напоминаний и координат и исключён из Git;
- телефон хранит применённый набор правил, registration status и audit journal.

Персональные напоминания и координаты не должны автоматически попадать в
публичный репозиторий.

## Ещё требуется

- создать первый реальный конечный набор правил;
- проверить его Python- и Android-валидаторами;
- импортировать по ADB;
- визуально сверить все правила и зоны на телефоне;
- сравнить SHA-256 локального `rules.local.json` с SHA правил в диагностике;
- выполнить test notification и проверить lifecycle в журнале;
- проверить регистрацию через реальные Google Play services;
- провести физические `ENTER`, `EXIT`, `DWELL`;
- проверить reboot re-registration;
- проверить Samsung `Optimized`/Deep sleeping behavior;
- провести многодневное измерение батареи.

Все следующие проверки выполняются из готового prerelease без локальной Android-сборки.
