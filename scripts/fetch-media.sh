#!/usr/bin/env bash
#
# Fetches the exercise animation GIFs from the exercises dataset into a local cache directory.
#
# Why this exists: the app deliberately ships no binaries (see ExerciseMedia). Animations are
# streamed on demand and cached by Coil. That is enough for the shipped app, but it means the app
# cannot work offline and the first play of each exercise waits on the network.
#
# Use this script when you want a warm local cache for testing, for an offline demo, or to inspect
# the media before wiring anything up. It writes to a directory you choose and never touches the
# repository.
#
#   ./scripts/fetch-media.sh                       # the full 1,324 exercise set
#   ./scripts/fetch-media.sh 0025 0652 0334        # only these dataset ids
#   ./scripts/fetch-media.sh --out /tmp/ip-media   # choose the destination
#
# The media is © Gym visual (https://gymvisual.com/) and redistributed in the dataset with
# permission. Keep the attribution intact wherever you display it.

set -euo pipefail

REPO="hasaneyldrm/exercises-dataset"
BRANCH="main"
BASE="https://raw.githubusercontent.com/${REPO}/${BRANCH}"
INDEX="${BASE}/data/exercises.json"
OUT_DIR="${IRONPULSE_MEDIA_DIR:-$PWD/build/exercise-media}"
CONCURRENCY="${IRONPULSE_MEDIA_CONCURRENCY:-8}"

usage() {
  sed -n '2,25p' "$0" | sed 's/^# \{0,1\}//'
  exit "${1:-0}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --out)
      OUT_DIR="$2"
      shift 2
      ;;
    -h|--help)
      usage 0
      ;;
    -*)
      echo "unknown option: $1" >&2
      usage 1
      ;;
    *)
      break
      ;;
  esac
done

need() {
  command -v "$1" >/dev/null 2>&1 || { echo "missing required tool: $1" >&2; exit 1; }
}
need curl

# jq is the obvious tool here but is often missing on developer machines, so fall back to
# python, which is almost always present alongside an Android toolchain.
read_index() {
  if command -v jq >/dev/null 2>&1; then
    if [[ $# -gt 0 ]]; then
      jq -r --args '
        ($ARGS.positional) as $want
        | .[]
        | select(.id as $id | $want | index($id))
        | .gif_url
      ' "$1" "$@"
    else
      jq -r '.[].gif_url' "$1"
    fi
  else
    # Probe with a real run, not just `command -v`: on Windows the Store alias for `python`
    # resolves but fails when invoked, which would otherwise abort the whole script.
    local py=""
    for candidate in python3 python py; do
      if command -v "$candidate" >/dev/null 2>&1 && "$candidate" -c '' >/dev/null 2>&1; then
        py="$(command -v "$candidate")"
        break
      fi
    done
    if [[ -z "$py" ]]; then
      echo "need either jq or a working python to read the dataset index" >&2
      exit 1
    fi
    if [[ $# -gt 0 ]]; then
      "$py" -c '
import json, sys
want = set(sys.argv[2:])
for row in json.load(open(sys.argv[1], encoding="utf-8")):
    if row["id"] in want:
        print(row.get("gif_url", ""))
' "$1" "$@"
    else
      "$py" -c '
import json, sys
for row in json.load(open(sys.argv[1], encoding="utf-8")):
    print(row.get("gif_url", ""))
' "$1"
    fi
  fi
}

mkdir -p "$OUT_DIR"

echo "Downloading the dataset index..."
INDEX_FILE="$(mktemp)"
PATHS_FILE="$(mktemp)"
trap 'rm -f "$INDEX_FILE" "$PATHS_FILE"' EXIT
curl -fsSL "$INDEX" -o "$INDEX_FILE"

if [[ $# -gt 0 ]]; then
  # Only the requested ids, resolved out of the index so the caller uses dataset ids ("0025").
  read_index "$INDEX_FILE" "$@" | grep -v '^$' > "$PATHS_FILE"
  echo "Restricted to $(wc -l < "$PATHS_FILE" | tr -d ' ') requested id(s)."
else
  read_index "$INDEX_FILE" | grep -v '^$' > "$PATHS_FILE"
  echo "Dataset lists $(wc -l < "$PATHS_FILE" | tr -d ' ') exercises."
fi
FILTERED="$PATHS_FILE"

fetch_one() {
  local path="$1"
  local dest="${OUT_DIR}/${path#videos/}"
  if [[ -s "$dest" ]]; then
    echo "skip   ${path}"
    return 0
  fi
  mkdir -p "$(dirname "$dest")"
  if curl -fsSL --retry 3 --retry-delay 1 "$BASE/${path}" -o "$dest.tmp"; then
    mv "$dest.tmp" "$dest"
    echo "ok     ${path}"
  else
    rm -f "$dest.tmp"
    echo "FAILED ${path}" >&2
    return 1
  fi
}
export -f fetch_one
export BASE OUT_DIR

echo "Fetching into ${OUT_DIR} (concurrency ${CONCURRENCY})..."
# xargs keeps this POSIX friendly and parallel without pulling in GNU parallel.
if command -v xargs >/dev/null 2>&1; then
  if ! xargs -a "$FILTERED" -d '\n' -P "$CONCURRENCY" -I {} bash -c 'fetch_one "$@"' _ {}; then
    echo "Some downloads failed; re-run to retry only the missing ones." >&2
    exit 1
  fi
else
  while IFS= read -r path; do fetch_one "$path"; done < "$FILTERED"
fi

echo "Done. Media cached in ${OUT_DIR}"
du -sh "$OUT_DIR" 2>/dev/null || true
