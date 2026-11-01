Touching a file in this directory on an arena/** branch triggers the
"Emulator tests" GitHub Actions workflow (.github/workflows/emulator.yml),
which runs the instrumented (androidTest) suite on an API 33 emulator.

Run marker: 1.12.5 verification, run 4 - re-runs the instrumented suite after the
"pay a loan in any currency" change (external cross-currency payments, the Pay in
selector, and the stored paid side of a repayment). Runs 1-3 fixed build/test
mistakes: a missing import, a wrong parameter name, and a same-currency balance
check that the pay-currency refactor had stopped enforcing.
