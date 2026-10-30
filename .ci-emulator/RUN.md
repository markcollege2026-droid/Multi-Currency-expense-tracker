Touching a file in this directory on an arena/** branch triggers the
"Emulator tests" GitHub Actions workflow (.github/workflows/emulator.yml),
which runs the instrumented (androidTest) suite on an API 33 emulator.

Run marker: 1.12.4 verification, run 14 - re-runs the instrumented suite after the
lost-update fix in the view models (the atomic update() writes) and the new
settlement-rate refusal. Covers the widget provider listing and the side-by-side
debug package as before.
