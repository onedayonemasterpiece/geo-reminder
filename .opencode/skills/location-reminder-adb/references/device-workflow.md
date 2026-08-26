# Wired Samsung workflow

1. Confirm repository branch, commit and successful Android CI.
2. Enable Developer options and USB debugging on the intended S21 Ultra.
3. Connect by cable and accept the RSA prompt.
4. Require exactly one authorized device, or set `DEVICE_SERIAL`.
5. Install debug APK and open the application.
6. User grants precise location, “Allow all the time”, and notifications.
7. Import validated finite rules.
8. On the phone, inspect every configured rule and the registration summary.
9. Send a debug notification; inspect lifecycle in Journal.
10. Add an observation marker before every physical test.
11. Disconnect USB, start outside the circle, then cross it.
12. Reconnect after the observation window and export JSONL.
13. Diagnose the first missing journal stage.
14. Reboot and repeat one selected fresh rule.
15. Run battery measurement without continuous ADB.

Never use wireless ADB, root, bootloader changes, permission hacks, continuous
location collection or automatic journal deletion.
