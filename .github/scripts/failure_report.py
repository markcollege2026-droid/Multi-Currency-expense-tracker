"""Name the failing tests, so a red build is diagnosable from the commit comment.

Gradle only prints "testDebugUnitTest FAILED" and then the failing assertions,
which the log tail usually cuts off. GitHub is the only place these runs can be
read from, so the JUnit XML is summarised into the comment instead.
"""
import glob
import os
import xml.etree.ElementTree as ET
from pathlib import Path

lines = []
for path in sorted(glob.glob('app/build/test-results/testDebugUnitTest/TEST-*.xml')):
    for case in ET.parse(path).getroot().iter('testcase'):
        problems = list(case.findall('failure')) + list(case.findall('error'))
        for problem in problems:
            message = (problem.get('message') or '').strip().replace('\n', ' ')
            lines.append('- `' + (case.get('classname') or '') + '.' + (case.get('name') or '') +
                         '` — ' + message[:400])

body = ['## Failing tests (' + str(len(lines)) + ')'] if lines else \
    ['## Failing tests', 'No JUnit failures were recorded (the build failed before the tests, or in lint/assembly).']
body += lines[:40]

log = Path('build.log')
if log.exists():
    text = log.read_text(errors='replace')
    # Compile errors and the failing task are what actually say why the build
    # stopped; the stack traces in the tail do not. Print them first, unfiltered.
    interesting = [line for line in text.splitlines()
                   if line.startswith('e: ') or 'error:' in line.lower()
                   or 'Execution failed for task' in line or '> Task ' in line and 'FAILED' in line
                   or 'Lint found' in line or 'FAILURE:' in line]
    if interesting:
        body += ['', '## Build errors', '```'] + interesting[:60] + ['```']
    body += ['', '<details><summary>build.log (tail)</summary>', '', '```',
             text[-12000:], '```', '</details>']

Path('failure-summary.md').write_text('\n'.join(body))
if os.environ.get('GITHUB_STEP_SUMMARY'):
    with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as out:
        out.write('\n'.join(body))
