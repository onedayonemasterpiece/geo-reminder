# Протокол Samsung Galaxy S21 Ultra

## A. Устройство, сборка и разрешения

1. Записать модель, Android, API и One UI.
2. Подключить USB; `adb devices -l` должен показать ровно одно выбранное
   авторизованное устройство либо задаётся `DEVICE_SERIAL`.
3. Установить debug APK и открыть приложение.
4. Выдать precise foreground location.
5. В системных настройках выбрать location **Allow all the time**.
6. Разрешить notifications и проверить importance канала.
7. Оставить Samsung battery mode `Optimized`.
8. Выполнить `scripts/diagnose.sh` и экспортировать baseline journal.

## B. Детерминированный импорт

1. Начать с одной реальной наружной точки, а не категории.
2. Независимо проверить координаты; для первого теста использовать круг
   150–200 м.
3. Выставить `responsiveness_ms` 120000–180000.
4. Запустить Python validator.
5. Импортировать JSON по ADB.
6. В телефоне проверить список правил, координаты и SHA последнего импорта.
7. Выполнить test notification и проверить состояния journal lifecycle.

## C. Физический проход

1. На телефоне или через `scripts/mark-observation.sh` записать отметку
   «начало теста, нахожусь снаружи».
2. Убедиться, что телефон явно снаружи круга.
3. Отключить USB; приложение закрывать допустимо.
4. Войти в круг при обычном использовании телефона.
5. Записать реальное время пересечения и время уведомления.
6. Ждать не меньше responsiveness плюс несколько минут до классификации miss.
7. После окна наблюдения снова подключить USB и экспортировать JSONL.

Интерпретация:

- нет `GEOFENCE_EVENT_RECEIVED` — location stack не передал событие;
- событие есть, но `REMINDER_TRIGGER_SKIPPED` — смотреть reason;
- есть `NOTIFICATION_BLOCKED/FAILED` — notification path сломан;
- есть `ACTIVE_CONFIRMED` — ID был активен в Android;
- есть `TAPPED/DISMISSED` — зафиксировано пользовательское действие;
- есть `NO_LONGER_ACTIVE_UNKNOWN` — ID исчез без наблюдаемого действия.

## D. Once, repeat и cooldown

- `once`: после успешной доставки выйти и снова войти; второго уведомления быть
  не должно.
- `always`: повторный вход после cooldown должен создать новый notification ID.
- импорт того же once rule ID не сбрасывает completion.
- для намеренного нового напоминания используется новый rule ID либо
  `scripts/reset-rule-state.sh <rule-id>` с фиксацией в журнале.

## E. Reboot

1. Подготовить свежее активное правило.
2. Перезагрузить телефон без USB.
3. После обычной загрузки и разблокировки выполнить физический проход.
4. Экспортировать журнал и найти `SYSTEM_RESTORE_EVENT_RECEIVED`, затем
   `GEOFENCE_REGISTRATION_SUCCEEDED` с reboot reason.

## F. Батарея

1. Выполнить `scripts/reset-batterystats.sh`.
2. Использовать телефон 3–7 дней с небольшим набором зон.
3. Не держать UI открытым и не оставлять ADB подключённым.
4. Снять `scripts/battery-snapshot.sh`.
5. Сопоставить app-attributed расход с обычным шумом устройства.

## Samsung-specific escalation

Только после воспроизводимого miss:

1. проверить `Deep sleeping apps`;
2. исключить приложение из deep sleep;
3. повторить тот же маршрут с теми же radius/responsiveness;
4. не менять одновременно батарейный режим и геозону;
5. `Unrestricted` использовать только как контролируемое сравнение.
