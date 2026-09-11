"""Fail-closed AI lifecycle authority for PlaceMate-AI.

PlaceMate uses external/on-device inference surfaces (ML Kit image labeling/object
recognition and optional Gemini) but has no retained local optimizer/training loop.
That classification is evidence-backed, not a permanent exemption: if a local
training framework, backward pass, optimizer step, model fitting, or fine-tuning
surface appears in tracked source/build files, this authority fails until a real
training DAG is added.
"""
from __future__ import annotations

from dataclasses import asdict, dataclass
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SOURCE_SUFFIXES = {".kt", ".kts", ".java", ".py", ".toml", ".gradle", ".properties"}
SKIP_PARTS = {".git", ".gradle", ".idea", "build", ".training_control", "training_control", "releases"}

TRAINING_PATTERNS: tuple[tuple[str, re.Pattern[str]], ...] = (
    ("pytorch-training", re.compile(r"\b(torch|pytorch)\b.*\b(optimizer|backward|train|finetun)", re.I)),
    ("tensorflow-training", re.compile(r"\b(tensorflow|keras)\b.*\b(optimizer|fit|train|gradient)", re.I)),
    ("onnx-training", re.compile(r"\bonnxruntime[-_. ]?training\b", re.I)),
    ("optimizer", re.compile(r"\b(optimizer|optimiser)\.(step|zero_grad)|\bAdamW?\s*\(|\bSGD\s*\(", re.I)),
    ("backprop", re.compile(r"\.backward\s*\(|backpropagation|gradient[_ -]?descent", re.I)),
    ("train-loop", re.compile(r"\b(train|training)[_ -]?(epoch|loop|step)|early[_ -]?stopping", re.I)),
    ("local-finetune", re.compile(r"\b(fine[-_ ]?tun(e|ing)|lora|peft)\b", re.I)),
)
INFERENCE_PATTERNS: tuple[tuple[str, re.Pattern[str]], ...] = (
    ("mlkit-image-labeling", re.compile(r"mlkit\.image\.labeling|ImageLabeler", re.I)),
    ("mlkit-object-detection", re.compile(r"mlkit\.od\.detector|ObjectDetector", re.I)),
    ("gemini", re.compile(r"generative\.ai|GenerativeModel|Gemini", re.I)),
)


@dataclass(frozen=True, slots=True)
class Finding:
    path: str
    line: int
    category: str
    excerpt: str


@dataclass(frozen=True, slots=True)
class Audit:
    scanned_files: tuple[str, ...]
    inference_surfaces: tuple[Finding, ...]
    training_findings: tuple[Finding, ...]

    @property
    def complete(self) -> bool:
        # At least one AI inference surface must remain visible: otherwise this
        # authority would be silently classifying an unrelated Android app.
        return bool(self.inference_surfaces) and not self.training_findings

    def to_dict(self) -> dict[str, object]:
        return {
            "schema_version": 1,
            "repository": "Anurag9000/PlaceMate-AI",
            "classification": "external_and_on_device_inference_only",
            "scanned_files": list(self.scanned_files),
            "inference_surfaces": [asdict(row) for row in self.inference_surfaces],
            "training_findings": [asdict(row) for row in self.training_findings],
            "complete": self.complete,
            "local_trainable_surface_present": bool(self.training_findings),
            "source_configuration_only": True,
            "execution_claim_emitted": False,
        }


def _files(root: Path):
    for path in sorted(root.rglob("*")):
        if not path.is_file() or path.name == "run_all_training.py":
            continue
        relative = path.relative_to(root)
        if any(part in SKIP_PARTS for part in relative.parts):
            continue
        if path.suffix.lower() in SOURCE_SUFFIXES or path.name in {"gradle.properties"}:
            yield path


def audit(root: Path = ROOT) -> Audit:
    scanned: list[str] = []
    inference: list[Finding] = []
    training: list[Finding] = []
    for path in _files(root):
        relative = path.relative_to(root).as_posix()
        scanned.append(relative)
        for line_number, line in enumerate(path.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
            for category, pattern in INFERENCE_PATTERNS:
                if pattern.search(line):
                    inference.append(Finding(relative, line_number, category, line.strip()[:240]))
            for category, pattern in TRAINING_PATTERNS:
                if pattern.search(line):
                    training.append(Finding(relative, line_number, category, line.strip()[:240]))
    return Audit(tuple(scanned), tuple(inference), tuple(training))


def require_inference_only(root: Path = ROOT) -> Audit:
    result = audit(root)
    if not result.inference_surfaces:
        raise RuntimeError("PlaceMate authority found no tracked ML Kit/Gemini inference surface")
    if result.training_findings:
        evidence = "; ".join(
            f"{row.path}:{row.line}:{row.category}" for row in result.training_findings[:50]
        )
        raise RuntimeError(
            "PlaceMate now contains a retained local training surface and requires a real "
            f"training DAG: {evidence}"
        )
    return result
