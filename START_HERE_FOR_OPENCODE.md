# Первое задание для OpenCode

Используй project skill `location-reminder-adb` и правила из `AGENTS.md`.
Код приложения, журнал и GitHub Actions уже находятся в репозитории. Твоя первая
задача — не перепроектировать runtime, а собрать его, установить на подключённый
Samsung Galaxy S21 Ultra и наполнить фактическим конечным набором правил.

1. Проверь актуальный branch/commit и GitHub Actions; не теряй чужие изменения.
2. Выполни `adb devices -l`. До мутаций должно быть одно авторизованное
   устройство либо явно задан `DEVICE_SERIAL`; подтверди модель.
3. Собери debug APK или скачай artifact успешного GitHub Actions run.
4. Установи APK. Я вручную выдам precise location, background location
   **Allow all the time** и notifications; не обходи разрешения.
5. Получи от меня конкретный конечный список мест и текстов напоминаний.
6. Для каждой точки найди и перепроверь координаты. Не расширяй категории и не
   угадывай; сомнительные записи оставляй `enabled: false`.
7. Создай `config/rules.local.json`, проверь Python validator и покажи сводку
   rule ID / zone ID / label / coordinates / radius.
8. Импортируй по ADB и на экране телефона сверяй все заведённые правила.
9. Запусти test notification. В журнале должны быть attempt и результат
   системной проверки; не называй это физическим geofence test.
10. Запиши контрольную отметку, отключи USB и проведи физический проход снаружи
    внутрь зоны.
11. После observation window экспортируй JSONL и локализуй этап сбоя по
    `docs/JOURNAL_AND_DEBUGGING.md`.
12. Отдельно проверь reboot и многодневную батарею в Samsung `Optimized`.

Для первого Android/Gradle/ADB прохода используй GPT-5.6 Sol с reasoning `high`.
После устойчивой сборки и установки создание новых конечных `rules.local.json`
можно делать на Terra medium/high.
