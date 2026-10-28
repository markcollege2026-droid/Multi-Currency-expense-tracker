"""Builds emu-comment.json: instrumented-test XML results + gradle log tail.

Used by .github/workflows/emulator.yml so failures are readable on the commit
without downloading Actions logs/artifacts.
"""
import glob
import json
import os
import re

ANSI = re.compile(r"\x1b\[[0-9;]*[A-Za-z]")

parts = ["**Emulator test report**\n"]
xmls = sorted(glob.glob("app/build/outputs/androidTest-results/connected/**/*.xml", recursive=True))
if not xmls:
    parts.append("\n(no androidTest result XML found - tests may not have started)\n")
for xml in xmls:
    parts.append(f"\n--- {xml} ---\n")
    parts.append(ANSI.sub("", open(xml, errors="replace").read())[-6000:])
if os.path.exists("emulator.log"):
    parts.append("\n--- emulator.log (tail) ---\n")
    parts.append(ANSI.sub("", open("emulator.log", errors="replace").read())[-20000:])
body = "".join(parts)[-45000:]
open("emu-comment.json", "w").write(json.dumps({"body": body}))
print("wrote emu-comment.json", len(body))
