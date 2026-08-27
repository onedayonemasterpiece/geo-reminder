# Первое задание для OpenCode

Используй project skill `location-reminder-adb` и правила из `AGENTS.md`.

## Роль OpenCode в этом проекте

OpenCode **не разрабатывает и не собирает Android-приложение локально**. Код,
проверки и APK готовятся в GitHub. Локальная задача ограничена четырьмя вещами:

1. скачать репозиторий и последний проверенный debug prerelease;
2. проверить SHA-256 и установить APK по проводному ADB;
3. помочь выдать разрешения и проверить уведомление/журнал;
4. после этого сформировать конечный `config/rules.local.json` с указанными
   пользователем местами.

Не устанавливай JDK, Gradle, Android SDK, Android Studio или эмулятор. Не
исправляй Kotlin/Gradle/Actions локально. При дефекте собери диагностику и верни
её пользователю для исправления через ChatGPT + GitHub.

## Модель

Для обычного развёртывания и наполнения используй:

```text
GPT-5.6 Terra
reasoning: medium
```

Переходи на GPT-5.6 Sol/high только при устойчивой нетривиальной проблеме ADB,
One UI, разрешений или интерпретации журнала. Локальная сборка не является
основанием повышать модель: она здесь вообще не выполняется.

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

   Оба скрипта сами выбирают последний опубликованный debug prerelease,
   скачивают APK и checksum из GitHub, проверяют SHA-256 и выполняют
   `adb install -r`. Они не удаляют приложение и не очищают его данные.
5. Я вручную выдам precise location, background location **Allow all the time**
   и notifications. Не обходи системные экраны.
6. Запусти тестовое уведомление и проверь цепочку в журнале:

   ```bash
   ./scripts/test-notification.sh
   ./scripts/diagnose.sh
   ./scripts/export-journal.sh
   ```

7. Если установка или runtime не работают, не редактируй исходники. Сохрани:
   release tag, APK SHA-256, `adb devices -l`, полный stdout/stderr, релевантный
   `adb logcat`, экран диагностики и JSONL-экспорт.

## Наполнение после успешной установки

1. Получи от меня конечный список конкретных мест и текстов напоминаний.
2. Для каждой точки найди и перепроверь координаты. Не расширяй категории и не
   угадывай; сомнительные записи оставляй `enabled: false`.
3. Создай `config/rules.local.json` и проверь:

   ```bash
   python3 scripts/validate-rules.py config/rules.local.json
   ```

4. Покажи сводку rule ID / zone ID / label / coordinates / radius.
5. Импортируй правила:

   ```bash
   ./scripts/import-rules.sh config/rules.local.json
   ```

6. На телефоне сверяй все правила, затем проводи unplugged physical test,
   reboot test и наблюдение батареи по `docs/SAMSUNG_S21_ULTRA_TEST.md`.
