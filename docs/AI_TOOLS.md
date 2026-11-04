# AI tools

IronPulse gives its AI models a small, fixed set of application-owned tools so an answer can be
based on the athlete's real exercise library, current routine and completed workout history instead
of a guess. The tools are defined by the application, not by a provider: Google, OpenAI, Anthropic,
OpenRouter, xAI and a custom OpenAI-compatible endpoint all see the same tool catalog, the same
validation and the same limits.

Personal-data tools are available only after **Settings -> AI Data Access -> Allow AI Coach to
access workout data** is explicitly enabled. The dispatcher enforces that permission on every
request; a system prompt is not the security boundary.

The implementation lives in
[`app/src/main/java/com/example/data/ai/AiTools.kt`](../app/src/main/java/com/example/data/ai/AiTools.kt).

---

## What the model can and cannot do

| Property | Behavior |
|---|---|
| Access | Retrieval is read-only. The only write-shaped tool creates a proposal; it does not write until the athlete approves the exact displayed change. |
| Data surface | `AiDataAccess`, implemented by `RepositoryAiDataAccess` over narrowly scoped `FitnessRepository` reads. |
| SQL | None. The model cannot send a query; the repository methods are fixed. |
| Filesystem | None. |
| Network | None. A tool handler has no HTTP client and cannot choose a host. |
| Code execution | None. |
| Tool set | Fixed at build time by `AiToolCatalog`; a provider cannot add or rename a tool. |
| Permissions | The dispatcher checks `AiAccessPolicy` and `AiToolDomain` before every call. |
| Result size | Default **16 KiB**, 32 KiB for workout/history tools and 48 KiB for the routine overview; results also cap sets and text fields. |

A tool call is validated and executed by `AiToolDispatcher`. Failures become a JSON object with
`"ok": false` that the model can read and recover from; they are not thrown into the chat as raw
exceptions. Cancellation is the exception: it is always rethrown so closing a screen stops the work.

---

## Execution model

`AiConversationEngine` runs the loop that asks the provider for an answer, executes any requested
tools, sends the results back, and repeats until the model answers or a limit is reached.

| Limit | Value |
|---|---|
| Provider rounds per conversation turn | 4 |
| Executed tool calls per turn | 12 |
| Per-request timeout | 90 seconds |
| Retries for a retryable provider failure | 1 |
| Duplicate calls | Answered once per conversation from a cache keyed by tool name and canonicalized arguments |
| Ordering | Sequential; calls in one response run one after another |
| Exhausted budget | One final request with tools removed, so the athlete still gets an answer from the data already gathered |
| Permission changes | The dispatcher re-reads the access policy before each tool call, so revoking access stops a pending retrieval inside an active loop |
| Proposal stop | A successful routine proposal ends the tool loop so the app can render the approval card; the model resumes only after the athlete decides |

### Tool modes

| Mode | Behavior |
|---|---|
| **Auto** (default) | Use the provider's native tool protocol. If the provider clearly rejects the tools field, retry in the constrained text protocol. |
| **Native only** | Use only the provider's structured tool protocol; a rejection is reported as an error. |
| **Text fallback** | Use only the constrained text envelope, useful for an endpoint with a partial OpenAI surface. |

Native tool calling is **not universal**. The model selector reports it as supported, unsupported or
unknown from catalog metadata, and the adapters still handle a real rejection. Compatibility mode is
the bounded fallback; it is never displayed to the athlete as prose.

---

## Common argument rules

Arguments arrive as a JSON object. Before a handler runs, `AiToolArgs` enforces:

- Required strings must be present, a JSON string, non-blank after trimming, and no longer than the
  tool's stated maximum.
- Optional strings may be absent, `null` or blank; blank is treated as absent. A non-string value is
  an error.
- Integer arguments accept a JSON number or a numeric string. A fractional value, a non-finite value
  or an out-of-range value is an error.
- Ids must be whole and positive.
- An unknown extra property is ignored by the dispatcher. The published schema sets
  `additionalProperties: false` so a well-behaved model does not send one.
- Arguments that are not a valid JSON object produce `invalid_arguments`. An empty argument string
  is treated as `{}`.

All errors use this shape:

```json
{
  "ok": false,
  "error": "invalid_arguments",
  "message": "Argument \"limit\" must be between 1 and 20."
}
```

| Error code | Meaning |
|---|---|
| `unknown_tool` | The requested name is not in the catalog. The message lists the available tools. |
| `invalid_arguments` | The JSON could not be parsed, a required argument is missing, a type or range is wrong, or a value is too long. |
| `not_found` | The requested exercise or routine does not exist. |
| `no_routines` | The athlete has no saved routines, so a routine lookup cannot return one. |
| `no_active_routine` | No routine is currently scheduled, or the active routine has no weekly days. |
| `permission_denied` | The tool is not available because AI workout-data access is disabled. |
| `exercise_not_in_day` / `invalid_day` | The proposed routine change does not match the current routine. |
| `exercise_not_found` / `exercise_exists` | A replacement must be a real catalog exercise or a genuinely new generated exercise. |
| `ambiguous_replacement` | A proposal cannot both select an existing exercise and create a new one. |
| `tool_failed` | The handler failed for an unexpected reason. The message is truncated and does not include a stack trace. |
| `result_too_large` | The serialized result exceeded its per-tool cap. The model is told to narrow the request. |

---

## Tools

### `search_exercises`

**Purpose:** find real catalog movements by name, muscle group, body part, target muscle or
equipment before recommending one.

| Argument | Type | Required | Rules |
|---|---|---|---|
| `query` | string | yes | 1–120 characters after trimming; must not be blank. |
| `limit` | integer | no | 1–20, default 8. |

**Matching:** the query is lower-cased and split on whitespace. Every token must appear somewhere in
the name, category, body part, target or equipment. Ranking is deterministic: exact name, then name
prefix, then name substring, then all tokens in the name, then exact category, body part, target or
equipment, then a weaker match. Ties are sorted by name.

**Success result:**

```json
{
  "ok": true,
  "query": "bench press",
  "count": 2,
  "exercises": [
    {
      "id": 12,
      "name": "Barbell Bench Press",
      "category": "Chest",
      "bodyPart": "Chest",
      "target": "Pectoralis Major",
      "equipment": "Barbell",
      "exerciseType": "weight_reps"
    }
  ],
  "note": ""
}
```

When nothing matches, `count` is 0 and `note` suggests using fewer words. An empty query is an
`invalid_arguments` error rather than a dump of the catalog.

---

### `get_exercise_details`

**Purpose:** read the full record of one exercise, including instructions, before explaining or
programming it.

| Argument | Type | Required | Rules |
|---|---|---|---|
| `exerciseId` | integer | one of these | Whole, positive. |
| `name` | string | one of these | 1–160 characters after trimming. |

If both are supplied, `exerciseId` wins. If neither is supplied, the call fails with
`invalid_arguments`.

**Success result:**

```json
{
  "ok": true,
  "exercise": {
    "id": 12,
    "name": "Barbell Bench Press",
    "category": "Chest",
    "bodyPart": "Chest",
    "target": "Pectoralis Major",
    "secondaryMuscles": ["Triceps", "Front Delts"],
    "equipment": "Barbell",
    "exerciseType": "weight_reps",
    "instructions": "Step-by-step instructions…",
    "instructionsTruncated": false,
    "description": "Short description…",
    "isCustom": false
  }
}
```

Instructions are capped at 4,000 characters and the description at 1,000; `instructionsTruncated`
says whether text was cut. A missing id or name returns `not_found`.

---

### `list_exercises`

**Purpose:** browse a page of the library, optionally filtered, to see what the athlete actually has.

| Argument | Type | Required | Rules |
|---|---|---|---|
| `category` | string | no | 1–60 characters; case-insensitive exact match. |
| `equipment` | string | no | 1–80 characters; case-insensitive partial match. |
| `limit` | integer | no | 1–50, default 20. |
| `offset` | integer | no | 0–5,000, default 0. |

Results are sorted by lower-cased name. The response reports the full filtered `total`, the requested
`offset`, the page `count` and `hasMore`.

```json
{
  "ok": true,
  "total": 1334,
  "offset": 0,
  "count": 20,
  "hasMore": true,
  "exercises": [ { "id": 1, "name": "…", "category": "…", "bodyPart": "…", "target": "…", "equipment": "…", "exerciseType": "…" } ]
}
```

`hasMore` is `offset + count < total`. A page beyond the end returns `count: 0` and `hasMore: false`.

---

### `get_routine`

**Purpose:** read one saved routine and its training days, including each entry's sets, reps, holds
and per-entry rest default.

| Argument | Type | Required | Rules |
|---|---|---|---|
| `routineId` | integer | no | Whole, positive. |
| `name` | string | no | 1–160 characters; case-insensitive. |

Selection order:

1. `routineId`, when supplied.
2. Exact case-insensitive name, then a case-insensitive name containing the supplied text.
3. With neither selector, the most recent saved routine; `selectedMostRecent` is then `true`.

No saved routines at all returns `no_routines`. A selector that matches nothing returns `not_found`.

**Success result (abridged):**

```json
{
  "ok": true,
  "selectedMostRecent": false,
  "routine": {
    "id": 4,
    "name": "Push / Pull / Legs",
    "description": "…",
    "goal": "Hypertrophy",
    "daysPerWeek": 6,
    "createdAt": 1760000000000,
    "days": [
      {
        "weekday": 0,
        "day": "Monday",
        "title": "Push",
        "note": "…",
        "exercises": [
          {
            "name": "Barbell Bench Press",
            "equipment": "Barbell",
            "sets": 4,
            "reps": 8,
            "weightKg": 0.0,
            "addedWeightKg": 0.0,
            "holdSeconds": 0,
            "restSeconds": 45,
            "prescription": "4 sets x 8 reps"
          }
        ]
      }
    ]
  }
}
```

`restSeconds` is the routine-entry default (0 means the rest timer stays manual). It is read from
the routine JSON, not from the shared exercise definition, so the same movement can rest differently
in two routines.

---

### `list_routines`

**Purpose:** list saved routines with a compact summary before answering questions about the plan.

| Argument | Type | Required | Rules |
|---|---|---|---|
| `limit` | integer | no | 1–50, default 20. |

```json
{
  "ok": true,
  "total": 3,
  "count": 3,
  "routines": [
    {
      "id": 4,
      "name": "Push / Pull / Legs",
      "goal": "Hypertrophy",
      "daysPerWeek": 6,
      "createdAt": 1760000000000,
      "exerciseCount": 22
    }
  ]
}
```

`exerciseCount` is the number of entries across all seven days. The full plan is only returned by
`get_routine`, which keeps this listing small.

---

### `search_recent_workouts`

**Purpose:** retrieve bounded completed-session data for recent-training, volume, frequency and
session questions.

| Argument | Type | Required | Rules |
|---|---|---|---|
| `daysBack` | integer | no | 1–365, default 30. |
| `exerciseName` | string | no | Case-insensitive exercise filter. |
| `limit` | integer | no | 1–20, default 10. |
| `offset` | integer | no | 0–200, default 0. |

Returns session id/title/date/start/end/duration, routine link, completed set count, and up to 120
completed sets per session with set number, exercise identity/type, weight, added weight, reps,
hold seconds, cardio minutes, distance and body-weight snapshot. `hasMore` identifies another page.

---

### `get_current_routine`

**Purpose:** return the active weekly schedule exactly as the routine feature sees it, including
rest days and exercise order.

| Argument | Type | Required | Rules |
|---|---|---|---|
| none | — | — | The active routine is read from preferences. |

The result contains `routineId`, `routineName`, and all seven days with weekday, title, rest-day
flag, note and each exercise's id, name, type, category, equipment, sets, reps, weight, added weight,
hold seconds, rest seconds, prescription and a bounded instruction excerpt. With no active routine
the tool returns `no_active_routine` instead of guessing.

---

### `search_exercise_history`

**Purpose:** inspect one exact exercise's completed performance for progress comparisons.

| Argument | Type | Required | Rules |
|---|---|---|---|
| `exerciseName` | string | yes | Exact name; variants such as “Push-up (Wide)” are not merged. |
| `daysBack` | integer | no | 1–1095, default 180. |
| `limit` | integer | no | 1–30, default 20. |

Returns sessions newest first with only the matching completed sets and the same performance fields
as `search_recent_workouts`. An empty result includes an explicit note rather than invented history.

---

### `propose_routine_modification`

**Purpose:** prepare one routine change for explicit approval. This tool **does not modify data**.

| Argument | Type | Required | Rules |
|---|---|---|---|
| `routineId` | integer | no | Must be the currently active routine. |
| `weekday` | integer | yes | 1 = Monday … 7 = Sunday. |
| `removeExerciseName` | string | yes | Must exist in that day. |
| `replacementExerciseId` | integer | conditional | Existing catalog exercise. |
| `replacementExerciseName` | string | yes | Name of the existing or generated replacement. |
| `newExercise` | object | conditional | Complete generated-exercise record; only when no catalog movement fits. |
| `reason` | string | no | Short explanation shown on the approval card. |

The response contains a structured proposal and the note that nothing has changed. The app renders
it inside the conversation. **Grant permission** triggers a fresh permission check, active-routine
check, routine-signature stale check and one transaction that either replaces the slot or inserts
the generated exercise and replaces the slot together. **Reject** changes nothing. A proposal is
one-time and cannot authorize a second edit.

---

## Adding a tool

Tools are intentionally centralized. To add one:

1. Add a narrow method to `AiDataAccess` and implement it in `RepositoryAiDataAccess` using
   `FitnessRepository`. Do not pass a DAO, database handle, file or HTTP client to a handler.
2. Add an `AiToolDefinition` to `AiToolCatalog` with a JSON Schema, a stable name, a description, a
   domain (`CATALOG`, `WORKOUT_DATA` or `ROUTINE_WRITE`) and an appropriate result cap.
3. Add a handler in `AiToolRegistry` and validate every argument through `AiToolArgs`.
4. Return a JSON object with `"ok": true` and bounded content.
5. Add dispatcher, permission and engine coverage in the AI tool tests.

A new write capability requires an explicit domain, an application-owned approval path and a
security review. `propose_routine_modification` is the only write-shaped tool; it has no direct
database access and cannot apply itself.
