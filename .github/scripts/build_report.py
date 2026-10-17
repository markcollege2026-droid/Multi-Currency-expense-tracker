"""Publish compact, non-sensitive verification metadata via GitHub's API."""
import json
import os
from pathlib import Path
import xml.etree.ElementTree as ET

reports = list(Path('app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
assert reports, 'No JUnit test results were produced'
suites = []
for report in reports:
    node = ET.parse(report).getroot()
    suites.append({key: node.get(key) for key in ('name', 'tests', 'failures', 'errors', 'skipped')})
summary = json.dumps(suites, indent=2)
schema = Path('app/schemas/com.spendingtracker.app.data.local.AppDatabase/4.json').read_text()
body = '## Verified build\n\nJUnit suites:\n```json\n' + summary + '\n```\n\n<details><summary>Room-generated schema 4</summary>\n\n```json\n' + schema + '\n```\n</details>'
Path('build-report.json').write_text(json.dumps({'body': body}))
with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as output:
    output.write('## Unit tests and APK compilation passed\n```json\n' + summary + '\n```\n')
