# Статус проверки

## Локально выполнено

- Python-валидатор прошёл на отключённом example rule set;
- Python bytecode compilation;
- `bash -n` для всех shell scripts;
- JSON parsing для schema/example;
- XML parsing для manifest/resources;
- pure Kotlin compilation для `Models.kt` и `GeofenceIds.kt`;
- example rules имеют `enabled: false`;
- debug ADB receiver отсутствует в main manifest;
- исходники не содержат foreground service или периодического location polling.

## Проверяется GitHub Actions

Workflow выполняет:

- `testDebugUnitTest`;
- `lintDebug`;
- `assembleDebug`;
- SHA-256 APK;
- публикацию APK, lint и test reports как artifact.

Результат конкретного commit нужно смотреть в checks/Actions; этот документ не
должен объявлять build зелёным до фактического успешного run.

## Требует физического Samsung S21 Ultra

- установка APK по ADB;
- выдача precise/background location и notification permission;
- регистрация через реальные Google Play services;
- физический `ENTER`, `EXIT`, `DWELL`;
- различение system event и notification lifecycle в журнале;
- reboot re-registration;
- Samsung `Optimized`/Deep sleeping behavior;
- многодневное измерение батареи.
