# START HERE FOR OPENCODE — Geo Reminder

## Цель текущей локальной сессии

Разработка приложения выполнена в ChatGPT + GitHub. Локальный OpenCode нужен
только для provisioning и аппаратной проверки на Samsung по проводному ADB.

Не устанавливай Android build tooling, не собирай APK локально и не редактируй
Kotlin/Gradle/Actions. Если обнаружен runtime-дефект — сохрани доказательства и
верни их в GitHub для следующей реализации.

## Актуальная сборка

```text
version: 0.2.0-debug
commit: f1904eeb83f30efb4276be49e3b972a056bfca65
tag: debug-f1904eeb83f3
package: com.onedayonemasterpiece.georeminder.debug
APK SHA-256: ee7da66aeb102976219517b6019c48e2853c197fc9ed9b4a2c54ef3a4680de13
```

Release:
https://github.com/onedayonemasterpiece/geo-reminder/releases/tag/debug-f1904eeb83f3

## Что проверяет 0.2.0

Эта версия разделяет два эффекта:

1. задержку доставки geofence event;
2. заметность уже опубликованного notification.

В ней есть новый канал `geo-reminders-v2`, кнопка прямого перехода в его
системные настройки и latency-поля в журнале:

- `triggering_location_to_receiver_ms`;
- `geofence_receiver_to_notification_attempt_ms`;
- `notification_attempt_to_posted_ms`;
- `geofence_receiver_to_notification_posted_ms`.

## Важное правило обновления: не тратить время на несовместимую подпись

GitHub-hosted debug builds пока не используют постоянный repository-pinned
signing key. Поэтому подпись уже установленной старой debug-версии и 0.2.0 может
не совпасть.

Перед любым удалением сначала сохранить всё, что можно восстановить:

```bash
./scripts/diagnose.sh
./scripts/export-journal.sh
```

Также сохранить существующий локальный `config/rules.local.json`, если он есть,
и зафиксировать текущую установленную версию.

После этого сделать **ровно одну** обычную попытку обновления через
`adb install -r` (штатный install script делает именно это). Если обновление
успешно — приложение не удалять.

Если Android возвращает именно signature/certificate incompatibility, например
`INSTALL_FAILED_UPDATE_INCOMPATIBLE` или явное `signatures do not match`, не
пробовать другие варианты обновления и не устанавливать signing/build tools.
Это считается доказанным mismatch.

В таком случае пользователь явно разрешил fresh reinstall **только Geo Reminder**:

```bash
adb uninstall com.onedayonemasterpiece.georeminder.debug
adb install <verified-0.2.0-apk>
```

Удалять можно только после успешного экспорта старого журнала и сохранения
реальных правил. `pm clear` запрещён. Другие пакеты не трогать.

После fresh install заново выдать permissions и импортировать сохранённые правила.
Старый журнал останется только в экспортированном файле; не утверждать, что он
сохранился внутри новой установки.

## Обязательный порядок

1. Прочитай `AGENTS.md` и `.opencode/skills/location-reminder-adb/SKILL.md`.
2. Убедись, что подключён ровно один authorized ADB device, либо явно задай
   `DEVICE_SERIAL`.
3. Если Geo Reminder уже установлен:
   - сними diagnostics;
   - экспортируй JSONL journal;
   - сохрани `config/rules.local.json`, если он существует;
   - зафиксируй установленную версию/package.
4. Запусти штатный установщик:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/install-latest-release.ps1
```

Он скачивает последний опубликованный `debug-*` prerelease, APK + checksum,
проверяет SHA-256 и делает одну обычную попытку `adb install -r`.
5. Если update прошёл — продолжай. Если доказан signature mismatch — сразу
   выполни fresh reinstall по правилу выше и больше не трать время на repair
   несовместимой установки.
6. После установки запусти приложение и убедись, что фактически установлено:

```text
version=0.2.0-debug
package=com.onedayonemasterpiece.georeminder.debug
notification_channel_id=geo-reminders-v2
notification_channel=HIGH
```

7. Выдай/проверь через Android UI:
   - precise location;
   - Location → Allow all the time;
   - notifications.
   Не обходи permission model shell-командами.
8. Если был fresh reinstall и существуют сохранённые реальные правила:

```bash
python3 scripts/validate-rules.py config/rules.local.json
./scripts/import-rules.sh config/rules.local.json
```

Сверь rules SHA в диагностике с локальным файлом и визуально проверь правила и
координаты на телефоне.
9. Открой **Диагностика → Настроить звук геонапоминаний**. Это должно открыть
   настройки `geo-reminders-v2`. Пользователь выбирает явно различимый системный
   Samsung notification sound. Не меняй DND/громкость скрыто.
10. Выполни test notification для существующего реального правила:

```bash
./scripts/test-notification.sh [rule-id]
```

Пользователь должен подтвердить, что услышал звук и способен узнавать его как
Geo Reminder.
11. Снова выполни:

```bash
./scripts/diagnose.sh
./scripts/export-journal.sh
```

Проверь, что diagnostics показывает фактический sound URI/presence, vibration и
importance нового channel.
12. Перед физическим проходом запиши observation mark:

```bash
./scripts/mark-observation.sh "0.2.0 physical geofence test started outside"
```

13. Отключи USB. Пользователь начинает явно снаружи одной известной зоны,
   пересекает её границу и фиксирует наблюдаемое время отдельно от системного
   timestamp.
14. После прохода подключи USB и снова экспортируй diagnostics/journal.
15. Для соответствующего geofence event выпиши:

```text
rule id / zone id
radius_m
responsiveness_ms
triggeringLocation.time_ms
triggering_location_to_receiver_ms
geofence_receiver_to_notification_attempt_ms
notification_attempt_to_posted_ms
geofence_receiver_to_notification_posted_ms
субъективное время, когда пользователь услышал звук
```

16. Не выдавай `triggeringLocation.time_ms` за точный момент физического входа.
   Это системный location sample, а не ground truth границы.
17. Только по результату измерения предлагай изменение radius или
   `responsiveness_ms`. Не компенсируй задержку continuous GPS, foreground
   location service или polling.
18. Дополнительно проверь reboot re-registration: перезагрузи телефон только
   после завершения основной проверки, открой приложение, сними diagnostics и
   убедись, что активные реальные правила снова зарегистрированы.

## Про собственный аудиофайл

В 0.2.0 отдельный bundled-файл не нужен: системный звук выбирается пользователем
в настройках channel и не требует новой сборки.

Если владелец позже хочет одинаковый фирменный звук на всех устройствах, **не
редактируй Android-исходники в локальной provisioning-сессии**. Локальный агент
может принять путь к скачанному аудиофайлу только как входной артефакт: проверить
формат/длительность, посчитать SHA-256 и сообщить путь/метаданные пользователю.
Сам файл затем нужно передать в ChatGPT/GitHub development flow для добавления в
Android resources и выпуска нового versioned channel (например
`geo-reminders-v3`). Существующий `v2` нельзя считать программно
перенастраиваемым после создания.

## Стоп-условия

Остановись и верни конкретные доказательства, если:

- checksum 0.2.0 не совпал;
- установка не проходит по причине, отличной от signature mismatch;
- fresh reinstall после доказанного mismatch не проходит;
- версия после установки не `0.2.0-debug`;
- кнопка channel settings не открывает `geo-reminders-v2`;
- test notification не создаётся при существующем валидном правиле;
- новый geofence event есть, а notification path завершается ошибкой;
- diagnostics или JSONL export ломаются.

Не останавливайся только потому, что первая `adb install -r` получила
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`: после сохранения состояния это ожидаемая
ветка для одного fresh reinstall, разрешённого пользователем.
