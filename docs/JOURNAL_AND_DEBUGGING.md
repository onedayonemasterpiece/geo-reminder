# Журнал и диагностика

## Цель

Журнал должен ответить не только на вопрос «уведомление было или нет», но и
локализовать этап, на котором оборвалась цепочка:

```text
регистрация геозоны
  -> событие от Google Play services
  -> сопоставление request ID с правилом
  -> проверки enabled/window/once/cooldown
  -> попытка notify()
  -> наличие в activeNotifications
  -> нажатие или смахивание
```

## Хранилище

SQLite-база `geo-reminder-audit.db` использует WAL и содержит:

- `audit_events` — последовательный технический журнал;
- `notification_deliveries` — одна строка на каждую попытку уведомления;
- `rule_state` — completion/cooldown для правил;
- `registration_state` — последняя операция регистрации, SHA правил и полный
  список ожидаемых request ID;
- `rule_imports` — история успешных и отклонённых импортов;
- `counters` — устойчивый генератор notification ID.

Автоматической ротации в MVP нет: частота событий мала, а полнота истории важнее.
Перед очисткой данных или удалением приложения журнал нужно экспортировать.

## Состояния уведомления

| Состояние | Что доказано | Что не доказано |
| --- | --- | --- |
| `ATTEMPTED` | создана запись и начата доставка | вызов Android ещё мог не состояться |
| `FAILED` | известна блокирующая ошибка | уведомление не было доставлено |
| `POSTED_UNCONFIRMED` | `NotificationManager.notify()` вернулся без исключения | моментальная сверка ещё не увидела ID |
| `ACTIVE_CONFIRMED` | ID присутствовал в `activeNotifications` | пользователь мог не заметить UI/звук |
| `TAPPED` | content intent открыл приложение | невозможно измерить осознанность реакции |
| `DISMISSED` | сработал delete intent | Android сообщает действие не во всех системных сценариях |
| `NO_LONGER_ACTIVE_UNKNOWN` | ранее подтверждённый ID больше не активен | причина исчезновения неизвестна |

При каждом открытии приложения и при экспорте выполняется повторная сверка с
`activeNotifications`.

## Ключевые события

- `GEOFENCE_REGISTRATION_STARTED/SUCCEEDED/FAILED`;
- `GEOFENCE_EVENT_RECEIVED`;
- `GEOFENCE_RULE_NOT_FOUND`;
- `GEOFENCE_TRANSITION_MISMATCH`;
- `REMINDER_TRIGGER_SKIPPED/ACCEPTED`;
- `NOTIFICATION_ATTEMPT`;
- `NOTIFICATION_BLOCKED/POST_FAILED`;
- `NOTIFICATION_ACTIVE_CONFIRMED`;
- `NOTIFICATION_POSTED_UNCONFIRMED`;
- `NOTIFICATION_TAPPED/DISMISSED`;
- `NOTIFICATION_SYSTEM_RECONCILED`;
- `REMINDER_STATE_COMMITTED`;
- `USER_OBSERVATION_MARK`.

## Почему отсутствие события тоже информативно

Невозможно записать событие, которое Android вообще не передал приложению.
Поэтому перед физическим проходом создаётся `USER_OBSERVATION_MARK` с временем и
описанием. Если после неё нет `GEOFENCE_EVENT_RECEIVED`, а последняя регистрация
успешна, проблема находится до приложения: геопозиционирование, радиус,
разрешения, Google Play services или политика устройства.

## Экспорт JSONL

С телефона используется кнопка **«Экспортировать журнал JSONL»**. Через ADB:

```bash
./scripts/export-journal.sh
```

Экспорт содержит:

1. metadata и полную текстовую диагностику;
2. текущий snapshot правил и SHA-256;
3. snapshot последней регистрации и request ID;
4. всю историю импортов;
5. все строки доставки уведомлений;
6. все audit events в хронологическом порядке.

Каждая строка — самостоятельный JSON-объект, поэтому файл удобен для grep,
Python, DuckDB или последующего анализа агентом.

## Приватность

Приложение не запрашивает периодические координаты и не строит маршрут.
Координата с точностью может попасть в журнал только как `triggeringLocation`,
которую Google Play services приложил к конкретному геособытию. Экспорт следует
считать чувствительным локальным диагностическим файлом.
