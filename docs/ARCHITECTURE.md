# Архитектура

## Поток управления

```text
естественная фраза пользователя
        |
        v
OpenCode + project skill
        |
        v
конечный проверенный rules.local.json
        |
        | проводной ADB только при настройке
        v
RuleStore + строгий RuleJson parser
        |
        v
GeofenceRegistrar -> Google Play services
        |
        | телефон отключён от компьютера
        v
GeofenceBroadcastReceiver
        |
        +-> AuditRepository (каждый этап)
        +-> NotificationHelper -> Android notification
```

## Компоненты

- `MainActivity` — три read-only раздела: правила, журнал, диагностика.
- `RuleJson` — строгая проверка типов, неизвестных полей, диапазонов, ID и общего
  лимита 100 зон.
- `RuleStore` — атомарный текущий `rules.json`, предыдущая версия и неизменяемые
  локальные snapshots каждого успешного импорта.
- `GeofenceRegistrar` — регистрация активного конечного набора кругов; сохраняет
  SHA правил и полный список ожидаемых request ID.
- `GeofenceBroadcastReceiver` — принимает `ENTER`, `EXIT`, `DWELL`, фиксирует
  одноразовую triggering location и выполняет детерминированные проверки.
- `NotificationHelper` — создаёт локальное уведомление, пишет lifecycle и
  сверяет ID с `NotificationManager.activeNotifications`.
- `NotificationInteractionReceiver` — фиксирует смахивание.
- `BootReceiver` — перерегистрирует правила после reboot/package replace.
- `AuditDatabase` / `AuditRepository` — постоянная SQLite/WAL-модель журнала.
- `AuditExporter` — полный JSONL snapshot.
- `AdbCommandReceiver` — присутствует только в debug variant и защищён системным
  permission `android.permission.DUMP`, доступным ADB shell.

## Энергетическая модель

Нет foreground service, таймера, сокета или периодического location request.
Приложение может не иметь живого процесса между событиями. Monitoring выполняет
Google Play services; процесс запускается кратко для broadcast и уведомления.
`responsiveness_ms` позволяет сознательно обменять задержку на энергопотребление.

## Регистрация без ложного первоначального входа

У `GeofencingRequest.Builder` явно задан `setInitialTrigger(0)`. Поэтому импорт,
перерегистрация и reboot сами по себе не должны создать `ENTER`, когда телефон
уже находится внутри круга. Физический тест начинается снаружи.

## Достоверность диагностики

Публичный Android API не возвращает приложению полный фактический реестр
активных геозон Google Play services. Поэтому приложение хранит доказательство
успешного вызова регистрации, request ID и последующие события, но честно
помечает `actual_android_registry=NOT_QUERYABLE_BY_PUBLIC_API`.

Аналогично приложение может подтвердить, что notification ID присутствовал в
`activeNotifications`, но не может доказать визуальное восприятие человеком.

## Граница

Отсутствуют сервер, MCP/FCM sync, аккаунт, PWA, category discovery, polygon
runtime, continuous tracking и Huawei/HMS provider. Следующая стадия может
добавить редкую доставку подписанного snapshot правил, не меняя локальный
geofence runtime и журнал.
