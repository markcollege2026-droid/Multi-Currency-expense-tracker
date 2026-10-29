Touching a file in this directory on an arena/** branch triggers the
"Emulator tests" GitHub Actions workflow (.github/workflows/emulator.yml),
which runs the instrumented (androidTest) suite on an API 33 emulator.

Run marker: 1.12.3 verification, run 12 - re-runs the instrumented suite on the
1.12.3 build (versionCode 16), which adds the widget-availability tests: the
provider being listed by AppWidgetManager (what the launcher's picker is built
from), the parsed minimum size, and the in-app pin path with its fallback. This
is also the first run where the debug build installs beside the release app as
com.spendingtracker.app.debug, so it proves that variant still launches and
still registers its widget receiver.
