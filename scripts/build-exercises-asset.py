"""
Builds app/src/main/assets/exercises.json from the hasaneyldrm/exercises-dataset.

The dataset is the source of truth for exercise media, targets and instructions. IronPulse adds
on top of it:
  * Title Case names, so the catalog matches the "Barbell Bench Press" casing the app shows.
  * A small set of curated additions the dataset does not carry at all (it has no weighted
    calisthenics: "Weighted Pull-up", "Weighted Dips", ... which the app logs as ADDED_WEIGHT).
  * exerciseType, which the dataset has no equivalent for, derived from the equipment plus the
    weighted-calisthenics name patterns.

Re-running it is safe and idempotent: the output is sorted by name and de-duplicated, so the asset
only changes when the input dataset or the rules below do. After regenerating the asset, bump
DatasetSeeder.DATASET_VERSION so installed apps actually reseed.

Usage:
    python scripts/build-exercises-asset.py <path-to-exercises-dataset/data/exercises.json>

Environment:
    IRONPULSE_EXERCISES_OUT   override the output path (defaults to app/src/main/assets/exercises.json
                              relative to the repository root)
"""
import json
import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.environ.get(
    "IRONPULSE_EXERCISES_OUT",
    os.path.join(REPO_ROOT, "app", "src", "main", "assets", "exercises.json"),
)

# dataset body_part -> IronPulse muscle group chip
CATEGORY_MAP = {
    "chest": "Chest",
    "back": "Back",
    "shoulders": "Shoulders",
    "upper arms": "Arms",
    "lower arms": "Arms",
    "waist": "Core",
    "upper legs": "Legs",
    "lower legs": "Legs",
    "cardio": "Calisthenics",
    "neck": "Shoulders",
}

# dataset equipment -> IronPulse exerciseType
STANDARD_EQUIPMENT = {
    "dumbbell", "cable", "barbell", "leverage machine", "smith machine", "kettlebell",
    "stability ball", "ez barbell", "sled machine", "medicine ball", "rope", "roller",
    "bosu ball", "olympic barbell", "wheel roller", "upper body ergometer",
    "skierg machine", "hammer", "stationary bike", "tire", "trap bar", "elliptical machine",
    "stepmill machine", "band", "resistance band",
}

# Bodyweight movements that IronPulse logs with added load. The dataset marks all of them
# "body weight", but they are the whole point of the weighted calisthenics feature.
WEIGHTABLE_PATTERNS = [
    "push-up", "pushup", "pull-up", "pullup", "chin-up", "chinup", "dip",
    "planche", "lever", "pistol", "dragon flag", "hindu", "archer",
    "typewriter", "commando", "l-sit", "calisthenics", "squat", "lunge",
    "pike push", "inverted row", "australian pull",
]

# Exercises the dataset does not contain. These carry the IronPulse weighted calisthenics
# vocabulary that the dataset omits entirely; media is empty on purpose, so the UI shows the
# placeholder instead of pretending a GIF exists.
CURATION_ONLY = [
    ("Weighted Pull-up", "Back", "back", "Dip Belt / Pull-up Bar", "STANDARD_WEIGHT"),
    ("Weighted Chin-up", "Back", "back", "Dip Belt / Pull-up Bar", "STANDARD_WEIGHT"),
    ("Weighted Wide-Grip Pull-up", "Back", "back", "Dip Belt / Pull-up Bar", "STANDARD_WEIGHT"),
    ("Weighted Dips", "Chest", "chest", "Dip Belt / Parallel Bars", "STANDARD_WEIGHT"),
    ("Weighted Chest Dips", "Chest", "chest", "Dip Belt / Parallel Bars", "STANDARD_WEIGHT"),
    ("Weighted Push-up", "Chest", "chest", "Weight Plate / Vest", "STANDARD_WEIGHT"),
    ("Weighted Ring Push-up", "Chest", "chest", "Gymnastic Rings & Vest", "STANDARD_WEIGHT"),
    ("Weighted Inverted Row", "Back", "back", "Bar & Vest", "STANDARD_WEIGHT"),
    ("Weighted Muscle-up", "Back", "back", "Dip Belt / Pull-up Bar", "STANDARD_WEIGHT"),
    ("Weighted Front Lever Row", "Back", "back", "Rings / Dip Belt", "STANDARD_WEIGHT"),
    ("Weighted Planche Lean", "Shoulders", "shoulders", "Weight Plate / Vest", "STANDARD_WEIGHT"),
    ("Weighted Pistol Squat", "Legs", "upper legs", "Dumbbell / Barbell", "STANDARD_WEIGHT"),
    ("Weighted Bulgarian Split Squat", "Legs", "upper legs", "Dumbbell", "STANDARD_WEIGHT"),
    ("Weighted Calf Raise", "Legs", "lower legs", "Weight Plate / Machine", "STANDARD_WEIGHT"),
    ("Weighted Plank", "Core", "waist", "Weight Plate / Vest", "STANDARD_WEIGHT"),
    ("Weighted Hanging Leg Raise", "Core", "waist", "Weight Plate", "STANDARD_WEIGHT"),
]

SMALL_WORDS = {"of", "and", "on", "in", "the", "a", "an", "to", "with", "at", "or", "for", "vs"}


def titleize(name):
    def cap(match):
        word = match.group(0)
        if word.lower() in SMALL_WORDS:
            return word.lower()
        return word[0].upper() + word[1:]
    return re.sub(r"[a-zA-Z']+", cap, name)


def titlecase_equipment(equipment):
    eq = (equipment or "").strip()
    if not eq:
        return "Bodyweight"
    if eq.lower() == "body weight":
        return "Bodyweight"
    return eq[0].upper() + eq[1:]


# Machine based equipment. The dataset files these under legs or cardio depending on the movement,
# but they can only be logged as distance work, which is what ExerciseType.CARDIO means.
CARDIO_EQUIPMENT = {
    "treadmill", "stepmill machine", "stationary bike", "elliptical machine",
    "skierg machine", "upper body ergometer",
}


def derive_exercise_type(equipment, name, body_part="", target=""):
    """Pick the logging type. Mirrors ExerciseType.isCardio on the Kotlin side."""
    eq = (equipment or "").strip().lower()
    if (body_part or "").strip().lower() == "cardio":
        return "CARDIO"
    if eq in CARDIO_EQUIPMENT:
        return "CARDIO"
    if (target or "").strip().lower() == "cardiovascular system":
        return "CARDIO"
    if eq == "weighted":
        return "ADDED_WEIGHT"
    if eq in ("body weight", "assisted"):
        lowered = (name or "").lower()
        if any(pattern in lowered for pattern in WEIGHTABLE_PATTERNS):
            return "ADDED_WEIGHT"
        return "PURE_BODYWEIGHT"
    if eq in STANDARD_EQUIPMENT:
        return "STANDARD_WEIGHT"
    return "STANDARD_WEIGHT"


def main():
    src = sys.argv[1] if len(sys.argv) > 1 else None
    if not src:
        print("usage: build_asset.py <exercises.json>")
        return 1

    with open(src, encoding="utf-8") as handle:
        data = json.load(handle)

    records = []
    seen_names = set()

    for row in data:
        instructions = row.get("instructions", {}) or {}
        english = re.sub(r"\s+", " ", instructions.get("en", "")).strip()
        secondary = row.get("secondary_muscles", []) or []
        equipment_raw = row.get("equipment", "")
        name = titleize(row["name"])

        # The dataset contains a few names that collide once Title Cased; keep the first.
        key = name.strip().lower()
        if key in seen_names:
            continue
        seen_names.add(key)

        body_part = row.get("body_part", row["category"])
        target = row.get("target", "")

        records.append({
            "id": row["id"],
            "name": name,
            "category": CATEGORY_MAP.get(row["category"], "Calisthenics"),
            "bodyPart": body_part,
            "equipment": titlecase_equipment(equipment_raw),
            "target": target,
            "secondaryMuscles": [s[0].upper() + s[1:] if s else s for s in secondary],
            "instructions": english,
            "gifUrl": row.get("gif_url", ""),
            "exerciseType": derive_exercise_type(equipment_raw, name, body_part, target),
        })

    next_id = max(int(r["id"]) for r in records) + 1
    for name, category, body_part, equipment, exercise_type in CURATION_ONLY:
        records.append({
            "id": str(next_id).zfill(4),
            "name": name,
            "category": category,
            "bodyPart": body_part,
            "equipment": equipment,
            "target": category,
            "secondaryMuscles": [],
            "instructions": "",
            "gifUrl": "",
            "exerciseType": derive_exercise_type(equipment, name, body_part, category),
        })
        next_id += 1

    records.sort(key=lambda r: r["name"].lower())

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as handle:
        json.dump(records, handle, ensure_ascii=False, separators=(",", ":"))

    names = {r["name"] for r in records}
    print("wrote", len(records), "exercises")
    print("size:", os.path.getsize(OUT), "bytes")
    print("unique names:", len(names))
    print("has Barbell Bench Press:", "Barbell Bench Press" in names)
    print("has Weighted Pull-up:", "Weighted Pull-up" in names)

    import collections
    types = collections.Counter(r["exerciseType"] for r in records)
    print("exercise types:", dict(types))
    # A regression guard: running must never come out as a barbell movement.
    for probe in ("Run", "Jump Rope", "Walking Treadmill"):
        hit = next((r for r in records if r["name"] == probe), None)
        print(f"  {probe}: {hit['exerciseType'] if hit else 'not in dataset'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
