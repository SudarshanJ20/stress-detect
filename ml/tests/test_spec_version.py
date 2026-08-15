"""Guard: the SPEC_VERSION in code must equal the one declared in docs/feature-spec.md.

Version drift is the exact failure the SPEC_VERSION field exists to catch — in Phase 6 a
code/doc/model mismatch means the Kotlin extractor and the trained ONNX model disagree
SILENTLY. This makes it a loud test, not a convention. A matching Android/Konsist test
(Kotlin SPEC_VERSION == docs/feature-spec.md) is added in Phase 5 (see docs/roadmap.md).
"""
import re
from pathlib import Path

from features.spec_constants import SPEC_VERSION

REPO = Path(__file__).resolve().parents[2]
SPEC_DOC = REPO / "docs" / "feature-spec.md"
_VER_RE = re.compile(r"SPEC_VERSION:\s*(v\d+\.\d+\.\d+)")


def _doc_spec_version() -> str:
    text = SPEC_DOC.read_text(encoding="utf-8")
    matches = _VER_RE.findall(text)
    assert matches, f"no 'SPEC_VERSION: vX.Y.Z' declaration found in {SPEC_DOC}"
    return matches[0]  # the header block is the first (and authoritative) declaration


def test_code_spec_version_matches_doc():
    doc = _doc_spec_version()
    assert SPEC_VERSION == doc, (
        f"SPEC_VERSION DRIFT — code (ml/src/features/spec_constants.py) = {SPEC_VERSION!r} "
        f"but docs/feature-spec.md declares {doc!r}. Bump BOTH together (and, from Phase 5, "
        f"the Android SPEC_VERSION + the ONNX export metadata). See feature-spec.md 'Versioning'."
    )


def test_code_spec_version_is_wellformed():
    assert _VER_RE.fullmatch(f"SPEC_VERSION: {SPEC_VERSION}"), \
        f"code SPEC_VERSION {SPEC_VERSION!r} is not vMAJOR.MINOR.PATCH"
