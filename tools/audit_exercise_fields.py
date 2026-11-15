#!/usr/bin/env python3
"""
Audit and assign explicit exercise fields to the bundled catalogue.

The database/tag type remains classification metadata; this script decides which input fields an
exercise can record. It is deliberately deterministic and writes a complete CSV audit so every
record is accounted for.

Run from the repository root:
    python tools/audit_exercise_fields.py
"""
from __future__ import annotations

import csv
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSET = ROOT / "app" / "src" / "main" / "assets" / "exercises.json"
AUDIT = ROOT / "docs" / "exercise_field_audit.csv"

F_REPS = "reps"
F_WEIGHT = "weight"
F_ADDED = "addedWeight"
F_HOLD = "holdSeconds"
F_TIME = "timeSeconds"
F_DISTANCE = "distanceKm"

WEIGHTED_EQUIPMENT = {
    "weighted",
    "weight plate",
    "weight plate / vest",
    "bar & vest",
    "gymnastic rings & vest",
    "dip belt / pull-up bar",
    "dip belt / parallel bars",
    "rings / dip belt",
}
BODYWEIGHT_EQUIPMENT = WEIGHTED_EQUIPMENT | {
    "bodyweight",
    "pull-up bar",
    "parallel bars",
    "gymnastic rings",
    "bar",
}
ASSISTED_EQUIPMENT = {"assisted", "band", "resistance band"}
NO_LOAD_PROP_EQUIPMENT = {
    "stability ball",
    "bosu ball",
    "roller",
    "wheel roller",
    "rope",
}
CARDIO_EQUIPMENT = {
    "treadmill",
    "stepmill machine",
    "stationary bike",
    "elliptical machine",
    "skierg machine",
    "upper body ergometer",
    "rowing machine",
}
STRETCH_WORDS = ("stretch", "mobility", "yoga", "foam roll", "pose", "flexibility")
STATIC_WORDS = (
    "plank",
    "side bridge",
    "hold",
    "dead hang",
    "wall sit",
    "isometric",
    "l-sit",
    "l sit",
    "v-sit",
    "v sit",
    "hollow",
    "planche",
    "front lever",
    "back lever",
    "human flag",
    "maltese",
    "iron cross",
    "static",
    "pinch",
    "squeeze",
    "balance board",
    "handstand",
    "flag",
)
DYNAMIC_WORDS = (
    "push-up",
    "push up",
    "pull-up",
    "pull up",
    "raise",
    "curl",
    "crunch",
    "row",
    "press",
    "squat",
    "lunge",
    "twist",
    "tap",
    "adduction",
    "abduction",
    "leg lift",
    "reach",
    "kick",
    "climb",
    "dip",
    "extension",
    "flexion",
    "rotation",
    "fly",
    "flye",
    "walk",
    "jump",
    "burpee",
    "sit-up",
    "sit up",
    "run",
    "cycle",
    "swing",
    "clean",
    "snatch",
    "thrust",
    "pullover",
    "shrug",
    "kickback",
    "pushdown",
    "good morning",
    "dynamic",
)
WEIGHTABLE_TOKENS = (
    "push up",
    "pull up",
    "chin up",
    "chin",
    "muscle up",
    "dip",
    "pistol",
    "dragon flag",
    "hindu",
    "archer",
    "typewriter",
    "commando",
    "l sit",
    "l-sit",
    "planche",
    "lever",
    "row",
    "squat",
    "lunge",
    "plank",
    "bridge",
    "raise",
    "handstand",
    "maltese",
    "flag",
)
CONDITIONING_TOKENS = (
    "battling rope",
    "battling ropes",
    "sprint",
    "sprints",
    "wind sprint",
    "wind sprints",
    "quick feet",
    "high knees",
    "bear crawl",
    "mountain climber",
    "burpee",
    "shuttle",
    "skater",
    "jumping jack",
)
CARRY_TOKENS = (
    "farmer",
    "farmers",
    "carry",
    "suitcase",
    "yoke",
    "sled push",
    "sled drag",
    "prowler",
    "waiter walk",
    "rack walk",
)
FIELD_OVERRIDES: dict[str, list[str]] = {
    # Self-resistance, no external load point.
    "1297": [F_HOLD],
    # Standing balance drill without a measurable load.
    "0020": [F_HOLD],
    # Curated weighted holds; names are unambiguous even without instructions.
    "5212": [F_HOLD, F_ADDED],
    "5216": [F_HOLD, F_ADDED],
}


def normalize(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", " ", text.lower()).strip()


def has_phrase(text: str, phrase: str) -> bool:
    return re.search(r"\b" + re.escape(phrase) + r"\b", text) is not None


def default_fields(exercise_type: str) -> list[str]:
    if exercise_type == "ADDED_WEIGHT":
        return [F_ADDED, F_REPS]
    if exercise_type == "PURE_BODYWEIGHT":
        return [F_REPS, F_HOLD]
    if exercise_type == "CARDIO":
        return [F_TIME, F_DISTANCE]
    return [F_WEIGHT, F_REPS]


def load_field(equipment: str) -> str | None:
    if equipment in NO_LOAD_PROP_EQUIPMENT or equipment in ASSISTED_EQUIPMENT:
        return None
    if equipment in BODYWEIGHT_EQUIPMENT:
        return F_ADDED
    return F_WEIGHT


def is_static_hold(name: str) -> bool:
    n = normalize(name)
    return any(has_phrase(n, word) for word in STATIC_WORDS) and not any(
        has_phrase(n, word) for word in DYNAMIC_WORDS
    )


def stretch_kind(name: str) -> str | None:
    n = normalize(name)
    if not any(has_phrase(n, word) for word in STRETCH_WORDS):
        return None
    if any(has_phrase(n, word) for word in DYNAMIC_WORDS):
        return "dynamic"
    return "static"


def is_conditioning(name: str) -> bool:
    n = normalize(name)
    return any(has_phrase(n, word) for word in CONDITIONING_TOKENS)


def is_carry(name: str) -> bool:
    n = normalize(name)
    return any(has_phrase(n, word) for word in CARRY_TOKENS)


def is_weightable_bodyweight(name: str, equipment: str) -> bool:
    if equipment not in BODYWEIGHT_EQUIPMENT:
        return False
    n = normalize(name)
    return any(has_phrase(n, word) for word in WEIGHTABLE_TOKENS)


def recommended_fields(rec: dict) -> tuple[list[str], list[str]]:
    """Return (fields, reasons)."""
    exercise_id = str(rec["id"])
    name = rec["name"]
    equipment = rec.get("equipment", "Bodyweight").strip().lower()
    exercise_type = rec.get("exerciseType", "STANDARD_WEIGHT")
    body_part = rec.get("bodyPart", "")
    target = rec.get("target", "")
    normalized_name = normalize(name)

    override = FIELD_OVERRIDES.get(exercise_id)
    if override is not None:
        return override, ["explicit catalogue override for an ambiguous movement"]

    if (
        exercise_type == "CARDIO"
        or body_part == "cardio"
        or target.strip().lower() == "cardiovascular system"
        or equipment in CARDIO_EQUIPMENT
    ):
        return [F_TIME, F_DISTANCE], ["cardio: duration and distance"]

    if is_carry(name):
        load = load_field(equipment)
        fields = ([load] if load else []) + [F_TIME, F_DISTANCE]
        return fields, ["loaded carry: load plus time and distance"]

    if is_conditioning(name):
        fields = [F_TIME]
        if any(
            has_phrase(normalized_name, word)
            for word in ("sprint", "sprints", "wind sprint", "wind sprints", "shuttle", "skater")
        ):
            fields.append(F_DISTANCE)
        return fields, ["conditioning: duration, not repetitions"]

    stretch = stretch_kind(name)
    if stretch == "static":
        return [F_HOLD], ["static stretch: hold duration only"]
    if stretch == "dynamic":
        if equipment in WEIGHTED_EQUIPMENT or exercise_type == "ADDED_WEIGHT":
            return [F_REPS, F_ADDED], ["dynamic loaded stretch movement: reps plus added weight"]
        return [F_REPS], ["dynamic mobility drill: repetitions only"]

    if is_static_hold(name):
        load = load_field(equipment)
        fields = [F_HOLD] + ([load] if load else [])
        return fields, ["static hold: duration plus the applicable load"]

    if equipment in NO_LOAD_PROP_EQUIPMENT:
        return [F_REPS], ["no-load prop: repetitions only; the prop has no mass field"]

    if is_weightable_bodyweight(name, equipment):
        return [F_REPS, F_ADDED], ["weightable bodyweight movement: reps plus added weight"]

    if exercise_type == "PURE_BODYWEIGHT":
        return [F_REPS], ["pure bodyweight repetition movement: reps only"]

    if exercise_type == "ADDED_WEIGHT":
        return [F_ADDED, F_REPS], ["added-weight movement: reps plus added weight"]

    return [F_WEIGHT, F_REPS], ["external-load movement: weight plus reps"]


def validate_fields(fields: list[str]) -> None:
    valid = {F_REPS, F_WEIGHT, F_ADDED, F_HOLD, F_TIME, F_DISTANCE}
    assert fields, "no fields"
    assert len(fields) == len(set(fields)), "duplicate field"
    assert all(field in valid for field in fields), "unknown field"
    assert not (F_WEIGHT in fields and F_ADDED in fields), "conflicting load fields"
    assert not (F_HOLD in fields and F_TIME in fields), "conflicting duration fields"


def main() -> None:
    records = json.loads(ASSET.read_text(encoding="utf-8"))
    changed = 0
    unchanged = 0
    rows = []
    for rec in records:
        existing = default_fields(rec.get("exerciseType", "STANDARD_WEIGHT"))
        fields, reasons = recommended_fields(rec)
        fields = list(dict.fromkeys(fields))
        validate_fields(fields)
        if fields == existing:
            unchanged += 1
        else:
            changed += 1
        added = [f for f in fields if f not in existing]
        removed = [f for f in existing if f not in fields]
        rows.append({
            "Exercise ID": rec["id"],
            "Exercise name": rec["name"],
            "Category": rec.get("category", ""),
            "Body part": rec.get("bodyPart", ""),
            "Target": rec.get("target", ""),
            "Exercise type": rec.get("exerciseType", ""),
            "Equipment": rec.get("equipment", ""),
            "Existing fields": ",".join(existing),
            "Recommended fields": ",".join(fields),
            "Added": ",".join(added),
            "Removed": ",".join(removed),
            "Reason": "; ".join(reasons),
            "Validation": "check:field-ids,check:duplicates,check:load-combination",
        })
        rec["fields"] = fields

    AUDIT.parent.mkdir(parents=True, exist_ok=True)
    with AUDIT.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)

    ASSET.write_text(json.dumps(records, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"records={len(records)} changed={changed} unchanged={unchanged}")
    print(f"audit={AUDIT}")


if __name__ == "__main__":
    main()
