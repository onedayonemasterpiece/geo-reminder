# GeoReminder — обратная связь 7 сентября 2026

Источник: голосовая заметка IdeaHub `voice-20260907-123002-522a15fb` —
«Оптимизация звуковых уведомлений для geo-reminder».

https://github.com/onedayonemasterpiece/idea-hub/blob/main/inbox/voice/2026/09/voice-20260907-123002-522a15fb.md

Канонический продуктовый контракт IdeaHub:

https://github.com/onedayonemasterpiece/idea-hub/blob/main/ideas/venture.new-product/idea-20260907-georeminder-notification-reliability.md

## Пользовательское наблюдение

При посещении нужного места напоминание не было замечено в момент прихода. Позже
пользователь увидел уже существующее push-уведомление. По одному такому наблюдению
нельзя считать доказанным, что это один дефект.

Разделяем две независимые характеристики:

1. **геосрабатывание** — сколько времени проходит до доставки geofence event в
   приложение;
2. **заметность** — насколько легко человек замечает уже опубликованное
   уведомление и понимает, что это именно GeoReminder.

Точное время физического входа, callback и первого показа в исходном наблюдении
не измерялось. Поэтому причина задержки и её величина пока не доказаны.

## Продуктовые требования

### 1. Не маскировать задержку геолокации звуком

Звук не считается исправлением latency. Сохраняем отдельную диагностику цепочки:

```text
фактическое пересечение границы
  -> triggeringLocation/time (если Android его передал)
  -> GEOFENCE_EVENT_RECEIVED
  -> REMINDER_TRIGGER_ACCEPTED
  -> NOTIFICATION_ATTEMPT
  -> ACTIVE_CONFIRMED / POSTED_UNCONFIRMED
  -> TAPPED / DISMISSED / пользовательское наблюдение
```

Существующая low-power архитектура сохраняется: ради устранения задержки не
добавлять continuous GPS, foreground location service или polling. Поле
`responsiveness_ms` остаётся настройкой запроса Geofencing API, но не должно
трактоваться как гарантированный SLA доставки.

### 2. GeoReminder должен иметь узнаваемый звуковой сигнал

Нужно, чтобы по звуку среди обычных уведомлений было понятно: стоит открыть
GeoReminder. Направление принято — отдельное явно различимое звуковое поведение
для геонапоминаний.

В версии 0.2.0 для этого создан новый channel `geo-reminders-v2` и прямой переход
в его системные настройки. Пользователь может выбрать любой системный Samsung
notification sound без новой сборки.

Отдельный bundled sound пока не выбран и в APK не включён. Если потребуется
одинаковый звук на разных устройствах, нужен аудиофайл и следующий versioned
channel. Существующий channel после создания нельзя считать программно
перенастраиваемым.

### 3. Не превращать напоминание в сирену

Без отдельного продуктового решения не добавлять бесконечный loop, alarm-style
foreground playback, обход DND или самопроизвольное повышение громкости.

Пользователь управляет звуком через системные настройки канала. DND/тихий режим
Android не обходятся.

## Реализовано в 0.2.0

App commit:
`f1904eeb83f30efb4276be49e3b972a056bfca65`.

GitHub Actions `Android CI #20`, run `34148227917`: validation, unit tests,
lint, `assembleDebug`, artifact и prerelease — PASS.

Release:
https://github.com/onedayonemasterpiece/geo-reminder/releases/tag/debug-f1904eeb83f3

APK SHA-256:
`ee7da66aeb102976219517b6019c48e2853c197fc9ed9b4a2c54ef3a4680de13`.

В коде:

- новый channel `geo-reminders-v2`, `IMPORTANCE_HIGH`, vibration и системный
  default notification sound;
- кнопка **«Настроить звук геонапоминаний»**;
- diagnostics выводит channel ID, importance, sound URI, vibration,
  `canBypassDnd`, audio usage и legacy channel;
- diagnostics показывает `responsiveness_ms` активных правил;
- `GEOFENCE_EVENT_RECEIVED` сохраняет `receiver_received_at_ms` и при наличии
  `triggeringLocation` вычисляет `triggering_location_to_receiver_ms`;
- notification path пишет
  `geofence_receiver_to_notification_attempt_ms`,
  `notification_attempt_to_posted_ms` и
  `geofence_receiver_to_notification_posted_ms`;
- `GEOFENCE_ZONE_MATCHED` пишет radius и `responsiveness_ms`;
- добавлены unit tests для вычисления latency;
- versionCode `2`, versionName `0.2.0`.

Полный release record:
[`RELEASE_0.2.0_2026-09-07.md`](RELEASE_0.2.0_2026-09-07.md).

## Что осталось проверить на телефоне

Кодовый пакет и APK готовы, но аппаратная приёмка 0.2.0 ещё не выполнена.
Нужно:

1. установить последний prerelease через существующий provisioning script;
2. проверить `version=0.2.0-debug` и channel `geo-reminders-v2`;
3. выбрать различимый системный Samsung sound;
4. выполнить test notification и подтвердить слышимость/узнаваемость;
5. провести реальный вход в геозону;
6. экспортировать journal и сравнить новые latency-поля;
7. только по измерению решать, менять ли radius или `responsiveness_ms`.

Критерий готовности не меняется: звук и geofence latency проверяются отдельно;
системный `triggeringLocation.time_ms` не выдаётся за точный момент физического
пересечения границы; DND/opt-out сохраняются; continuous GPS не добавлен.
