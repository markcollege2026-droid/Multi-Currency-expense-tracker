Touching a file in this directory on an arena/** branch triggers the
"Emulator tests" GitHub Actions workflow (.github/workflows/emulator.yml),
which runs the instrumented (androidTest) suite on an API 33 emulator.

Run marker: 1.12.1 verification, run 9 - re-runs the instrumented suite on the
1.12.1 build (versionCode 14), which is also the build that changes the debug
applicationId to com.spendingtracker.app.debug. The instrumented suite installs
and drives that variant, so this run is what proves the side-by-side debug
package still works (activity launch, widget receiver, singleTop entry).
