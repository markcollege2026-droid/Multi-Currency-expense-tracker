Touching a file in this directory on an arena/** branch triggers the
"Emulator tests" GitHub Actions workflow (.github/workflows/emulator.yml),
which runs the instrumented (androidTest) suite on an API 33 emulator.

Run marker: 1.12.0 verification, run 8 - re-runs the instrumented suite after the
loading-state/toast pass (AppToast, AppBusyRow, Manage-accounts busy row).
