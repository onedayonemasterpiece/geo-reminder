# Geo Reminder — project instructions

## Scope

This repository is a local Android geofence experiment, not a production cloud
service. Preserve these boundaries unless the user explicitly starts a later
phase:

- no server, MCP transport, FCM, PWA, account, cloud database, or remote sync;
- no foreground location service, periodic GPS, polling, sockets, or route
  history;
- only an explicitly finite list of circular geofences;
- no category expansion such as “all supermarkets”;
- no polygon runtime in schema v1;
- the ADB receiver must remain debug-only;
- journal history must not be silently deleted or collapsed into an ambiguous
  “notification shown” flag.

## Required order

1. Read `.opencode/skills/location-reminder-adb/SKILL.md`.
2. Run `python3 scripts/validate-rules.py <file>` before every import.
3. Run `adb devices -l`; mutate only with exactly one authorized device or an
   explicit `DEVICE_SERIAL`.
4. Build/install debug APK and let the user grant permissions through Android
   UI. Do not bypass the permission model.
5. Import via `scripts/import-rules.sh`.
6. Confirm every configured rule and coordinate on the phone screen.
7. Run a test notification, diagnostics, and JSONL export.
8. Before a physical route, write `USER_OBSERVATION_MARK` through the phone or
   `scripts/mark-observation.sh`.
9. Perform the unplugged physical test, then export and interpret the full event
   chain.
10. Check reboot and battery behavior separately.

## Journal invariants

- Persist imports, registration attempts/results, geofence events, rule
  decisions, notification attempts, active-system verification, taps, dismissals
  and unexplained disappearance.
- `ACTIVE_CONFIRMED` means the ID was present in Android
  `activeNotifications`; never claim that this proves the human saw or heard it.
- Absence of `GEOFENCE_EVENT_RECEIVED` after an observation marker is evidence
  that the transition was not delivered to application code.
- Keep JSONL export backward-readable and append new fields rather than silently
  changing meanings.
- Do not add automatic retention cleanup during the MVP.

## Safety and data

- Never run `adb root`, unlock the bootloader, modify other packages, enable
  wireless ADB, or collect continuous location.
- Coordinates must be tied to an explicitly named place and verified. Leave
  unresolved entries disabled; never guess.
- Treat exported JSONL as sensitive because a geofence event may contain one
  triggering location sample.
- ADB is provisioning/diagnostics only. The unplugged phone must remain fully
  functional.
- Use moderate, logically complete commits and push checkpoints.

## Verification

```bash
python3 scripts/validate-rules.py config/rules.example.json
./scripts/build-debug.sh
./scripts/install-debug.sh
./scripts/import-rules.sh config/rules.local.json
./scripts/test-notification.sh [rule-id]
./scripts/mark-observation.sh "physical test started outside"
./scripts/diagnose.sh
./scripts/export-journal.sh
```
