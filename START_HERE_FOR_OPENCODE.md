# Первое задание для OpenCode

Используй project skill `location-reminder-adb` и правила из `AGENTS.md`.

## Роль OpenCode

OpenCode **не разрабатывает и не собирает Android-приложение локально**. Код,
проверки и APK готовятся в GitHub. Локальная задача ограничена следующим:

1. скачать репозиторий и последний проверенный debug prerelease;
2. проверить SHA-256 и установить APK по проводному ADB;
3. помочь выдать разрешения и проверить UI/журнал;
4. сформировать конечный `config/rules.local.json` по конкретным указаниям
   пользователя, импортировать его и провести аппаратные тесты.

Не устанавливай JDK, Gradle, Android SDK, Android Studio или эмулятор. Не
исправляй Kotlin/Gradle/Actions локально. При дефекте собери диагностику и верни
её пользователю для исправления через ChatGPT + GitHub.

## Где хранится состояние

Есть ровно три уровня, их нельзя смешивать:

1. **Репозиторий — канонический контракт:**
   - `config/rules.schema.json` — машиночитаемая схема;
   - `config/rules.example.json` — отключённый безопасный пример;
   - `.opencode/skills/location-reminder-adb/references/rule-contract.md` —
     смысл полей;
   - этот файл и project skill — порядок работы.
2. **Локальная копия — желаемая пользовательская конфигурация:**
   `config/rules.local.json`. Файл создаётся только после получения реальных
   напоминаний и конкретных мест, исключён из Git и не должен попадать в
   публичный репозиторий.
3. **Телефон — применённое состояние и доказательства:** текущие импортированные
   правила, registration status и постоянный audit journal.

Не создавай вторую ручную копию правил вне рабочей директории. После импорта
сравни SHA-256 локального `rules.local.json` с SHA правил в диагностике телефона.

## Модель

Для обычного развёртывания и наполнения используй:

```text
GPT-5.6 Terra
reasoning: medium
```

Переходи на GPT-5.6 Sol/high только при устойчивой нетривиальной проблеме ADB,
One UI, разрешений или интерпретации журнала.

## Развёртывание

1. Клонируй репозиторий и переключись на
   `feat/android-geofence-audit-mvp-20260826`.
2. Прочитай `.opencode/skills/location-reminder-adb/SKILL.md`.
3. Подключи разблокированный Samsung Galaxy S21 Ultra по USB и добейся ровно
   одного устройства со статусом `device` в `adb devices -l`.
4. На Windows PowerShell выполни:

   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts/install-latest-release.ps1
   ```

   На Bash/Git Bash/WSL выполни:

   ```bash
   ./scripts/install-debug.sh
   ```

   Скрипты выбирают последний опубликованный debug prerelease, скачивают APK,
   checksum и build metadata, проверяют SHA-256 и выполняют `adb install -r`.
   Они не удаляют приложение и не очищают его данные.
5. Пользователь вручную выдаёт precise location, background location
   **Allow all the time** и notifications. Не обходи системные экраны.
6. Проверь экраны «Напоминания», «Журнал» и «Диагностика», затем перезапусти
   приложение и убедись, что журнал сохраняется.

Если диагностика показывает `rules=0`, это корректное состояние нового
устройства. `TEST_NOTIFICATION` в текущем APK требует существующего правила с
зоной. Не создавай фиктивную точку и не считай отсутствие тестового уведомления
поломкой: сначала подготовь первое реальное правило по следующему разделу.

## Наполнение реальными правилами

1. Получи от пользователя конечный список конкретных мест и тексты
   напоминаний. Не расширяй запрос до категорий вроде «любой супермаркет».
2. Для каждой точки найди и перепроверь координаты. Не угадывай; сомнительные
   записи оставляй `enabled: false`.
3. Создай локальный файл из канонического примера.

   Windows PowerShell:

   ```powershell
   Copy-Item config/rules.example.json config/rules.local.json
   ```

   Bash:

   ```bash
   cp config/rules.example.json config/rules.local.json
   ```

4. Замени все примерные значения, сохрани стабильные rule/zone ID и проверь:

   ```bash
   python3 scripts/validate-rules.py config/rules.local.json
   ```

5. До импорта покажи пользователю сводку:
   `rule ID / title / transition / repeat / zone ID / label / coordinates / radius`.
6. Импортируй:

   ```bash
   ./scripts/import-rules.sh config/rules.local.json
   ./scripts/diagnose.sh
   ```

7. На телефоне сверяй все правила, зоны, координаты, радиусы и registration
   status. Сравни локальный SHA-256 файла с SHA правил на телефоне.
8. Только после успешного импорта проверь notification plumbing:

   ```bash
   ./scripts/test-notification.sh [rule-id]
   ./scripts/export-journal.sh
   ```

   В журнале должны появиться attempt и результат `FAILED`,
   `POSTED_UNCONFIRMED` либо `ACTIVE_CONFIRMED`. Это ещё не физический geofence
   test.
9. Затем проводи unplugged physical test, reboot test и наблюдение батареи по
   `docs/SAMSUNG_S21_ULTRA_TEST.md`.

При любой runtime-проблеме не редактируй исходники локально. Сохрани release tag,
APK SHA-256, точную команду, stdout/stderr, релевантный `adb logcat`, экран
диагностики и JSONL-экспорт и верни их пользователю.