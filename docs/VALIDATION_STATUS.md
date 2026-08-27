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
- публикация immutable-by-convention debug prerelease.

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

## CI-процесс

В репозитории оставлен один build workflow. Он запускается один раз на push в
`main` или `feat/**`, выполняет tests/lint/build, публикует Actions artifact и —
для implementation-ветки — prerelease с тремя стабильными assets:

- `geo-reminder-debug.apk`;
- `geo-reminder-debug.apk.sha256`;
- `build-info.json`.

Дублирующий PR workflow удалён. Android setup не устанавливает emulator или
устаревший пакет `tools`; загружаются только необходимые command-line tools,
platform 36 и build-tools 36.0.0.

## Требует физического Samsung Galaxy S21 Ultra

- установка APK по ADB;
- выдача precise/background location и notification permission;
- проверка экранов «Напоминания», «Журнал» и «Диагностика»;
- тестовое уведомление и его цепочка в постоянном журнале;
- регистрация через реальные Google Play services;
- физический `ENTER`, `EXIT`, `DWELL`;
- reboot re-registration;
- Samsung `Optimized`/Deep sleeping behavior;
- многодневное измерение батареи.

Эти проверки выполняются из готового prerelease без локальной Android-сборки.
