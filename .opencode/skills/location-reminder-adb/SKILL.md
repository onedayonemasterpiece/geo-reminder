---
name: location-reminder-adb
description: Download, provision, author finite local rules for, inspect, and physically test the released Android geofence reminder app over wired ADB with its persistent audit journal.
---

# Location Reminder ADB

Use this skill when the user asks to install/update the released prototype,
convert natural-language reminders into local Android rules, inspect configured
reminders, diagnose a missed notification, or run the Samsung S21 Ultra test.

## Read first

- [Rule contract](./references/rule-contract.md)
- [Device workflow](./references/device-workflow.md)
- [`docs/JOURNAL_AND_DEBUGGING.md`](../../../docs/JOURNAL_AND_DEBUGGING.md)
- [Test record](./templates/test-record.md)

## State ownership

Keep one source for each kind of state:

- versioned repository: schema, disabled example, documentation and scripts;
- local clone: `config/rules.local.json`, the desired user configuration;
- phone: applied rules, registration result and persistent audit journal.

`config/rules.local.json` is intentionally gitignored. Do not commit personal
reminders or location coordinates to the public repository. Do not maintain a
second hand-edited copy elsewhere. After import, compare the local file SHA-256
with the rules SHA shown by phone diagnostics.

## Fixed MVP boundary

- GitHub Actions is the build authority. Device provisioning downloads a
  published APK; do not install JDK, Gradle, Android SDK, Android Studio or an
  emulator locally.
- USB ADB is temporary provisioning and diagnostics, not operational runtime.
- Runtime is Google Play services geofencing plus local Android notifications.
- Do not introduce server/MCP transport, FCM, polling, continuous location,
  foreground service, account, cloud storage or PWA.
- Accept only a finite user-approved list of concrete places.
- Every zone is a circle; no silent polygon approximation or category expansion.
- Never exceed 100 active circles.
- Preserve the persistent journal. Never report `ACTIVE_CONFIRMED` as proof that
  a human saw or heard the notification.

## Workflow

### 1. Establish source, release and device state

Checkout the intended repository branch without modifying it. Confirm that a
published debug prerelease exists. Run `adb devices -l`; continue with mutations
only when exactly one device is authorized, or `DEVICE_SERIAL` is explicit.
Confirm the intended Samsung S21 Ultra. Never enable wireless ADB or run root.

### 2. Download and install the verified release

Windows PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/install-latest-release.ps1
```

Bash/Git Bash/WSL:

```bash
./scripts/install-debug.sh
```

The scripts select the newest non-draft debug prerelease, download APK,
checksum and build metadata, verify SHA-256 and use `adb install -r`. They must
never uninstall the package or clear app data. On checksum mismatch, missing
release or signature incompatibility, stop and report diagnostics rather than
building or patching locally.

The user grants precise location, background location “Allow all the time”, and
notifications through Android UI. Never bypass this.

### 3. Verify installed UI and persistent journal

Open «Напоминания», «Журнал» and «Диагностика». Relaunch the app and confirm the
journal remains visible.

A fresh install may correctly show `rules=0`. In the current APK the debug test
notification requires an existing rule with at least one zone. Do not create a
fake coordinate merely to make this check green. Record the zero-rule state and
continue to author the first real user-approved rule.

### 4. Normalize the reminder

Create a deterministic rule with stable rule ID, title/message, transition,
repeat mode, cooldown/active window and explicitly enumerated places. Reject or
narrow open categories such as “any supermarket”.

### 5. Resolve concrete places

For every place provide stable zone ID, label, latitude, longitude, radius and a
short provenance note. Do not guess. If ambiguous, keep the rule disabled. For a
normal outdoor urban point, start around 120–180 m and tune from observations.

### 6. Generate and validate local desired state

Create the local file from the versioned example.

Windows PowerShell:

```powershell
Copy-Item config/rules.example.json config/rules.local.json
```

Bash:

```bash
cp config/rules.example.json config/rules.local.json
```

Replace every example value, then run:

```bash
python3 scripts/validate-rules.py config/rules.local.json
```

Preserve rule IDs when completion/cooldown state should survive edits. Use a new
ID, or an explicit reset command, only when the user intends a new reminder.
Before import show the user a compact table of every rule and zone.

### 7. Import and prove desired/applied parity

```bash
./scripts/import-rules.sh config/rules.local.json
./scripts/diagnose.sh
```

On the phone verify every rule, zone label, coordinate, radius, repeat mode and
latest registration status. Compare SHA-256 of `config/rules.local.json` with the
rules SHA in diagnostics. Do not continue on mismatch.

### 8. Verify notification plumbing after real import

```bash
./scripts/test-notification.sh [rule-id]
./scripts/export-journal.sh
```

Require a journal row for the attempt and inspect `FAILED`,
`POSTED_UNCONFIRMED`, or `ACTIVE_CONFIRMED`. This is not a geofence test.

### 9. Run a physical boundary test

```bash
./scripts/mark-observation.sh "outside zone; physical test started"
```

Disconnect USB, start clearly outside the circle, cross the boundary and record
real times. Registration disables initial triggers, so provisioning must not
simulate an entry. Reconnect only after the observation window and export JSONL.

Diagnose by the first missing stage:

- no `GEOFENCE_EVENT_RECEIVED`: event never reached app code;
- `REMINDER_TRIGGER_SKIPPED`: inspect exact reason;
- notification `FAILED`: permission/channel path;
- `ACTIVE_CONFIRMED`: ID existed in Android system UI;
- `TAPPED`/`DISMISSED`: interaction observed;
- `NO_LONGER_ACTIVE_UNKNOWN`: disappeared without observed interaction.

### 10. Reboot and battery

Verify boot re-registration separately. Start with Samsung `Optimized`; only
after reproducible misses inspect Deep sleeping apps. Use batterystats scripts
for a 3–7 day run and never keep ADB connected during the energy experiment.

### 11. Escalate code defects instead of developing locally

For a code/build/runtime defect, collect release tag, APK SHA-256, device/OS/One
UI versions, exact commands, stdout/stderr, relevant `adb logcat`, diagnostics
and JSONL. Return that evidence to the user for a ChatGPT + GitHub change. Do not
edit Kotlin, Gradle or Actions in the local provisioning session.

## Model routing

Use GPT-5.6 Terra with reasoning `medium` for release download, ADB provisioning,
finite-list rule generation, validation and import. Use GPT-5.6 Sol/high only for
a persistent non-obvious ADB/One UI/platform fault or difficult journal analysis.
Local Android compilation is outside this skill's provisioning workflow.
