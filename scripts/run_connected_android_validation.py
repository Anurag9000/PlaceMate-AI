#!/usr/bin/env python3
"""Run connected Android tests when a usable adb device is present.

Absence of external hardware is not treated as a scientific pass.  The command
publishes a durable BLOCKED receipt and exits successfully so the repository's
host-side lifecycle can still complete without fabricating hardware evidence.
"""
from __future__ import annotations

import json
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "artifacts" / "training_control" / "connected_android_validation.json"


def _write(payload: dict[str, object]) -> None:
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    temporary = OUTPUT.with_suffix(OUTPUT.suffix + ".tmp")
    temporary.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    temporary.replace(OUTPUT)


def _devices(adb: str) -> list[str]:
    completed = subprocess.run([adb, "devices"], cwd=ROOT, text=True, capture_output=True, check=True)
    devices: list[str] = []
    for line in completed.stdout.splitlines()[1:]:
        pieces = line.split()
        if len(pieces) >= 2 and pieces[1] == "device":
            devices.append(pieces[0])
    return devices


def main() -> int:
    adb = shutil.which("adb")
    if adb is None:
        payload = {
            "schema_version": 1,
            "status": "BLOCKED",
            "reason": "adb_not_available",
            "executed": False,
            "passed": False,
        }
        _write(payload)
        print(json.dumps(payload, sort_keys=True))
        return 0
    devices = _devices(adb)
    if not devices:
        payload = {
            "schema_version": 1,
            "status": "BLOCKED",
            "reason": "no_authorized_connected_device",
            "executed": False,
            "passed": False,
        }
        _write(payload)
        print(json.dumps(payload, sort_keys=True))
        return 0
    subprocess.run(["./gradlew", "connectedDebugAndroidTest"], cwd=ROOT, check=True)
    payload = {
        "schema_version": 1,
        "status": "PASS",
        "executed": True,
        "passed": True,
        "devices": devices,
    }
    _write(payload)
    print(json.dumps(payload, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
