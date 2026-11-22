"""Checks the generated brand marks line up with each other.

Three things are generated from one table and have to agree: the drawable files, the `BrandCatalog`
entries and the `markOf` arms in `ModelMarks.kt`. A mismatch is invisible at compile time when the
drawable is missing but the arm exists (the app would crash inflating it) and merely ugly the other
way, so this checks all three against each other and against the script's table.

Run from the repo root: `python3 tools/lobe-icons/check.py`
"""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
DRAWABLE_DIR = ROOT / "zyn-chat-app/src/main/res/drawable"
CATALOG = ROOT / "zyn-chat-app/src/main/kotlin/com/zyn/chat/app/data/providers/BrandCatalog.kt"
MARKS = ROOT / "zyn-chat-app/src/main/kotlin/com/zyn/chat/app/ui/brand/ModelMarks.kt"


def catalog_ids() -> list[str]:
    return re.findall(r'^\s*id = "([^"]+)",', CATALOG.read_text(), re.MULTILINE)


def mark_ids() -> list[str]:
    """Ids in the `markOf` when-branches, i.e. the ones that claim a drawable."""
    body = MARKS.read_text().split("public fun markOf(", 1)[1].split("\n}", 1)[0]
    return re.findall(r'"([a-z0-9_]+)" -> R\.drawable\.ic_model_\1', body)


def drawable_ids() -> list[str]:
    return sorted(path.stem.removeprefix("ic_model_") for path in DRAWABLE_DIR.glob("ic_model_*.xml"))


def main() -> int:
    catalog, marks, drawables = catalog_ids(), mark_ids(), drawable_ids()
    problems: list[str] = []

    for label, ids in (("catalog", catalog), ("markOf", marks), ("drawables", drawables)):
        duplicates = sorted({value for value in ids if ids.count(value) > 1})
        if duplicates:
            problems.append(f"{label} has duplicate ids: {', '.join(duplicates)}")

    for name, missing in (
        ("in the catalog but with no drawable", sorted(set(catalog) - set(drawables))),
        ("in the catalog but with no markOf arm", sorted(set(catalog) - set(marks))),
        ("with a drawable but not in the catalog", sorted(set(drawables) - set(catalog))),
        ("with a markOf arm but no drawable", sorted(set(marks) - set(drawables))),
    ):
        if missing:
            problems.append(f"{len(missing)} brand(s) {name}: {', '.join(missing[:12])}")

    if problems:
        print("brand marks are inconsistent:")
        for problem in problems:
            print(f"  - {problem}")
        return 1

    print(f"ok: {len(catalog)} brands, {len(drawables)} drawables, {len(marks)} markOf arms, all aligned")
    return 0


if __name__ == "__main__":
    sys.exit(main())