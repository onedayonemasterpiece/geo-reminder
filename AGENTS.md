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
its SHA-256 and normally installs it with `adb install -r`. If the release is
missing or a runtime defect is found, collect evidence and return it for a
GitHub change. Never compensate by silently moving development into the local
session.

## Signature mismatch and bounded fresh reinstall

GitHub-hosted debug builds do not currently use a repository-pinned permanent
signing key. Therefore an already installed debug APK may have a different
certificate from a newer prerelease.

Do not spend time trying to bypass or repair Android package-signature checks.
Use this bounded decision path:

1. Before any destructive action, preserve all recoverable local state:
   - run diagnostics;
   - export the full JSONL journal;
   - preserve any existing local `config/rules.local.json` outside the app data;
   - record the installed package/version and the target release tag/SHA-256.
2. Try the normal verified update exactly once with `adb install -r` (the
   provided installer scripts already do this).
3. If it succeeds, continue without uninstalling.
4. If it fails specifically with a certificate/signature incompatibility such
   as `INSTALL_FAILED_UPDATE_INCOMPATIBLE` or an explicit "signatures do not
   match" message, treat the mismatch as proven. Do not retry variants of the
   update and do not install build/signing tooling.
5. In that proven mismatch case, the user explicitly authorizes a **fresh
   reinstall of this package only**:
   - confirm the diagnostics/journal export completed and the rules source is
     preserved;
   - run `adb uninstall com.onedayonemasterpiece.georeminder.debug` once;
   - install the already verified target APK normally;
   - re-grant permissions through Android UI;
   - re-import the preserved real rules if any;
   - verify the applied rules SHA and runtime diagnostics.
6. Never use `pm clear`. Never uninstall any other package.

A fresh reinstall intentionally destroys the old app-private database, so the
pre-uninstall export is mandatory. The exported history remains evidence; do not
claim it was preserved inside the new installation.

## Required device order

1. Read `.opencode/skills/location-reminder-adb/SKILL.md` and
   `START_HERE_FOR_OPENCODE.md`.
2. Run `adb devices -l`; mutate only with exactly one authorized device or an
   explicit `DEVICE_SERIAL`.
3. Preserve diagnostics/journal/rules from an existing installation before an
   update attempt.
4. Download the verified release and attempt the normal update once with
   `scripts/install-latest-release.ps1` on Windows or `scripts/install-debug.sh`
   on Bash/Git Bash/WSL.
5. If and only if a signature mismatch is proven, use the bounded fresh
   reinstall path above.
6. Let the user grant permissions through Android UI. Do not bypass the
   permission model.
7. Run a test notification, diagnostics and JSONL export before adding or
   changing real places.
8. Run `python3 scripts/validate-rules.py <file>` before every rule import.
9. Import via `scripts/import-rules.sh` and confirm every configured rule and
   coordinate on the phone screen.
10. Before a physical route, write `USER_OBSERVATION_MARK` through the phone or
    `scripts/mark-observation.sh`.
11. Perform the unplugged physical test, then export and interpret the full
    event chain.
12. Check reboot and battery behavior separately.

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
- `adb uninstall` is forbidden during normal updates and allowed only for the
  single package `com.onedayonemasterpiece.georeminder.debug` after a proven
  signature mismatch, completed backup/export, and under the bounded procedure
  above.
- Never run `pm clear`.
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
