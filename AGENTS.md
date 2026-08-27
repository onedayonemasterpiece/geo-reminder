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

## Separate development from device provisioning

Application development, tests and APK assembly happen through ChatGPT + GitHub
and the repository workflow. A local OpenCode provisioning session must not
install Android build tooling, compile the app, edit Kotlin/Gradle/Actions or
create implementation commits.

The device workflow downloads the latest published debug prerelease, verifies
its SHA-256 and installs it with `adb install -r`. If the release is missing or a
runtime defect is found, collect evidence and return it for a GitHub change.
Never compensate by silently moving development into the local session.

## Required device order

1. Read `.opencode/skills/location-reminder-adb/SKILL.md`.
2. Download and install the verified release with
   `scripts/install-latest-release.ps1` on Windows or `scripts/install-debug.sh`
   on Bash/Git Bash/WSL.
3. Run `adb devices -l`; mutate only with exactly one authorized device or an
   explicit `DEVICE_SERIAL`.
4. Let the user grant permissions through Android UI. Do not bypass the
   permission model.
5. Run a test notification, diagnostics and JSONL export before adding real
   places.
6. Run `python3 scripts/validate-rules.py <file>` before every rule import.
7. Import via `scripts/import-rules.sh` and confirm every configured rule and
   coordinate on the phone screen.
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
- Never run `adb uninstall` or `pm clear` as part of an update. Preserve the
  journal; `adb install -r` is the normal update path.
- Coordinates must be tied to an explicitly named place and verified. Leave
  unresolved entries disabled; never guess.
- Treat exported JSONL as sensitive because a geofence event may contain one
  triggering location sample.
- ADB is provisioning/diagnostics only. The unplugged phone must remain fully
  functional.

## Provisioning commands

Windows PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/install-latest-release.ps1
```

Bash/Git Bash/WSL:

```bash
./scripts/install-debug.sh
```

Then:

```bash
./scripts/test-notification.sh [rule-id]
./scripts/diagnose.sh
./scripts/export-journal.sh
python3 scripts/validate-rules.py config/rules.local.json
./scripts/import-rules.sh config/rules.local.json
./scripts/mark-observation.sh "physical test started outside"
```
