#!/usr/bin/env python3
"""One-command exhaustive AI lifecycle controller for PlaceMate-AI.

PlaceMate uses ML Kit image labeling/object detection and optional Gemini inference,
but it does not retain a local optimizer/training surface.  The central controller
therefore inventories those inference surfaces, fails closed if local training code
appears, executes the complete host-side Android verification/build lifecycle, and
records connected-device validation as PASS or BLOCKED without fabricating hardware
evidence.
"""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import urllib.request

ROOT = Path(__file__).resolve().parent
REPOSITORY = "Anurag9000/PlaceMate-AI"
CONTROLLER_COMMIT = "fd34a95d18892df7fb14d1efbb99076a7810fb91"
CONTROLLER_BLOB = "05ef472b29933f18e956c69dfb7e543921ddaff5"
CONTROLLER_URL = (
    f"https://raw.githubusercontent.com/Anurag9000/RigorousRAG/{CONTROLLER_COMMIT}/"
    "tools/universal_training_controller_entry.py"
)
RESTART = {"exact_resume": True, "deterministic": True, "idempotent": True, "atomic_outputs": True}


def job(job_id: str, command: list[str], *, depends: list[str] | None = None, phase: str = "validation", artifacts: list[str] | None = None) -> dict:
    return {
        "id": job_id,
        "command": command,
        "phase": phase,
        "family": "placemate/ai-inference-lifecycle",
        "device_capable": False,
        "depends_on": list(depends or []),
        "is_training_job": False,
        "resume_strategy": "restart_exact",
        "checkpoint_contract": dict(RESTART),
        "deterministic": True,
        "idempotent": True,
        "atomic_outputs": True,
        "early_stopping_applicable": False,
        "early_stopping_exception_reason": "inference/build/test lifecycle node; no local optimizer",
        "completion_artifacts": list(artifacts or []),
    }


PROFILE = {
    "repository": REPOSITORY,
    "scientific_authority": "training_control/placemate_ai_authority.py",
    "jobs": [
        job(
            "audit-ai-inference-authority",
            [sys.executable, "scripts/audit_ai_authority.py"],
            phase="audit",
            artifacts=["artifacts/training_control/ai_authority.json"],
        ),
        job(
            "jvm-unit-tests",
            ["./gradlew", "testDebugUnitTest"],
            depends=["audit-ai-inference-authority"],
            phase="test",
        ),
        job(
            "assemble-debug-apk",
            ["./gradlew", "assembleDebug"],
            depends=["jvm-unit-tests"],
            phase="build",
            artifacts=["app/build/outputs/apk/debug/app-debug.apk"],
        ),
        job(
            "assemble-instrumentation-apk",
            ["./gradlew", "assembleDebugAndroidTest"],
            depends=["jvm-unit-tests"],
            phase="build",
            artifacts=["app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"],
        ),
        job(
            "connected-device-validation",
            [sys.executable, "scripts/run_connected_android_validation.py"],
            depends=["assemble-debug-apk", "assemble-instrumentation-apk"],
            phase="hardware-validation",
            artifacts=["artifacts/training_control/connected_android_validation.json"],
        ),
    ],
    "preferred_training_entrypoints": [],
    "preferred_dataset_entrypoints": [],
    "dynamic_registry_covers": [
        "app/src/**/*.kt",
        "app/src/**/*.java",
        "app/build.gradle.kts",
        "gradle/libs.versions.toml",
        "training_control/placemate_ai_authority.py",
    ],
    "ignore_entrypoints": [
        "run_all_training.py",
        "scripts/audit_ai_authority.py",
        "scripts/run_connected_android_validation.py",
    ],
    "strict_coverage": True,
    "require_native_resume": True,
    "require_exact_resume": True,
    "require_training_exact_resume": True,
    "require_training_early_stopping": True,
    "require_well_formed_training_exemptions": True,
    "require_dag_enforcement": True,
    "require_model_surface_accounting": True,
    "require_workload_surface_accounting": True,
    "require_literal_opf_mechanism_parity": True,
    "require_registry_member_accounting": True,
    "require_dynamic_registry_accounting": True,
    "require_scientific_component_config_accounting": True,
    "require_declared_combination_accounting": True,
    "require_scientific_ontology_accounting": True,
    "require_declarative_scientific_source_accounting": True,
    "require_extended_scientific_component_accounting": True,
    "require_full_scientific_choice_accounting": True,
    "require_role_paradigm_protocol_accounting": True,
    "require_existing_job_targets": True,
    "require_source_proven_training_exact_resume": True,
    "require_source_proven_training_early_stopping": True,
    "require_all_retained_trainable_source_reachability": True,
    "auto_console_training_jobs": False,
    "auto_console_subcommand_jobs": False,
}


def blob(data: bytes) -> str:
    return hashlib.sha1(f"blob {len(data)}\0".encode("ascii") + data).hexdigest()


def atomic(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_bytes(data)
    os.replace(temporary, path)


def main() -> int:
    cache = ROOT / ".training_control" / "universal_training_controller_entry.py"
    if not cache.is_file() or blob(cache.read_bytes()) != CONTROLLER_BLOB:
        data = urllib.request.urlopen(CONTROLLER_URL, timeout=60).read()
        actual = blob(data)
        if actual != CONTROLLER_BLOB:
            raise RuntimeError(f"Pinned controller checksum mismatch: {actual} != {CONTROLLER_BLOB}")
        atomic(cache, data)
    profile = ROOT / ".training_control" / "placemate_ai_v37.json"
    atomic(profile, (json.dumps(PROFILE, indent=2, sort_keys=True) + "\n").encode("utf-8"))
    env = os.environ.copy()
    env.pop("TRAINING_CONTROL_PROFILE", None)
    env["TRAINING_CONTROL_PROFILE_FILE"] = str(profile)
    env["TRAINING_CONTROL_REPO_ROOT"] = str(ROOT)
    env.setdefault("TRAINING_CONTROL_TERMINATION_GRACE_SEC", "30")
    return subprocess.call([sys.executable, str(cache), *sys.argv[1:]], cwd=ROOT, env=env)


if __name__ == "__main__":
    raise SystemExit(main())
