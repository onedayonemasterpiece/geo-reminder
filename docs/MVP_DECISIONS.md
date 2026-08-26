# Решения MVP

## Принято

- нативное Android-приложение;
- первое устройство Samsung Galaxy S21 Ultra;
- проводной ADB как provisioning/diagnostic channel;
- конечный пользовательски проверяемый список мест;
- круговые геозоны и лимит 100;
- детерминированный локальный JSON;
- Google Play services Geofencing API;
- локальные уведомления;
- read-only UI правил, журнала и диагностики;
- постоянный SQLite/WAL-журнал без автоматического удаления;
- JSONL-экспорт;
- boot re-registration;
- GitHub Actions debug APK;
- отдельный физический и battery experiment.

## Отложено

- сервер и MCP control plane;
- push-синхронизация;
- production signing и Google Play publication;
- удалённое редактирование;
- PWA;
- POI/category expansion;
- полигоны;
- Huawei/HMS;
- multi-user accounts.

## Отклонено для MVP

- постоянно работающий агент на телефоне;
- foreground location service;
- частый polling;
- continuous GPS;
- server-side movement history;
- автоматическое обнаружение «любых супермаркетов»;
- единое недоказуемое состояние «уведомление показано».
