# Security Policy

**Please do not report security vulnerabilities through public GitHub issues, discussions, or pull
requests.** Reporting them privately is the only way we can make sure they are fixed before anyone
else finds them.

---

## Supported versions

| Version | Supported |
|---|---|
| 4.3.x | Yes |
| 4.2.x | No - upgrade to 4.3.x |
| 4.1.x | No - upgrade to 4.3.x |
| 4.0.x | No - upgrade to 4.3.x |
| 3.0.x | No - upgrade to 4.3.x |
| 2.x | No - upgrade to 4.3.x |

Only the latest released version receives security fixes. If you are on an older build, upgrade —
there is no patched older branch.

---

## Reporting a vulnerability

**Email: security@ironpulse.app** (or use GitHub's
[private vulnerability reporting](https://github.com/JuggyBob/IronPulse/security/advisories/new)
if it is enabled on this repository — prefer that route, as it creates a private thread
automatically).

### What to include

- **Type** — e.g. data exposure, injection, insecure storage, permission misuse.
- **Location** — file, function, screen.
- **Steps to reproduce.**
- **Impact** — what an attacker gains, and what a user loses.
- **Version or commit hash** you tested.
- **Any proof-of-concept**, plus any log or screenshot that helps.

### What to expect

| Stage | Target |
|---|---|
| Acknowledgement | **2 business days** |
| Initial assessment (severity, whether we agree) | **7 business days** |
| Fix released | **30 days** for high/critical, **90 days** for moderate/low |

We will keep you updated throughout and will tell you when a fix ships. We are happy to credit you
in the release notes if you would like — tell us the name you want used.

We will **not** pursue legal action against anyone who reports a genuine vulnerability in good
faith, agrees not to publicly disclose the issue before a fix is available, and does not access or
exfiltrate other users' data.

---

## Threat model

IronPulse is a **local-first, offline, single-user Android app**. There is no server, no account
system, and no analytics. This shapes what can realistically be a vulnerability:

| Asset | Where it lives | Threat |
|---|---|---|
| Workout history, weigh-ins, routines | SQLite in app-private storage | A backup being read; an exported file; a rooted device |
| AI provider API keys and custom endpoint URL | DataStore, entered by the user | Leaking to another provider or host; committing a key to a repo |
| Athlete profile | SQLite + DataStore | Exfiltration through an AI request the user did not consent to |
| Gallery media | Copied into `filesDir/exercise_media/` | Leaving a user's photo on disk after they remove it |
| Profile picture | Copied into `filesDir/profile_media/` | A copy of the user's photo outliving the app data wipe |
| Routines and chat history | SQLite | Loss or corruption; unintended deletion |

**Out of scope**, because there is nothing to attack:
- Any server-side component. There is no server.
- Multi-user isolation. There is one user per install.
- Physical access to an unlocked, unrooted device.
- Attacks on an external AI provider or a custom endpoint the user chooses.

---

## Security properties this app maintains

Worth knowing, because they are deliberate and should stay that way:

- **AI data sharing is off by default.** No profile data is sent with an AI request until the user
  explicitly enables it. `athleteContextPromptBlock()` returns an empty string while it is off.
- **AI Coach workout-data access is a separate, default-off permission.** Retrieval tools are not
  registered and direct dispatch is rejected while it is off. Disabling it also expires pending
  routine proposals.
- **Credentials stay with their provider.** Each provider has its own key and model in DataStore.
  A key is sent only to that provider's fixed host, or, for the custom endpoint, only to the exact
  normalized URL the user confirmed. Changing the URL requires a new confirmation.
- **Custom endpoints are opt-in and constrained.** The URL must be HTTP(S) with no embedded
  credentials or fragment. Plain HTTP is accepted only for loopback and private-network hosts, with
  an explicit warning. Redirects are not followed, and TLS certificate checks are never disabled.
- **AI retrieval tools are read-only.** The registry can search and read completed workouts, the
  current routine, the exercise catalog and exercise history through narrow repository methods.
  There is no SQL, filesystem, arbitrary network or code execution tool, and every call is validated,
  byte-capped and bounded.
- **AI routine changes require explicit approval.** The model can only create a proposal. A single
  displayed substitution is applied by the app after the user presses Grant permission, with a
  permission recheck, active-routine check, stale-signature check and one Room transaction. There is
  no general AI database-write tool.
- **The API key is never committed.** `.env`, `local.properties`, `*.jks` and `*.keystore` are
  gitignored, and CI scans every push for secrets.
- **Release signing keys are not in the repository.**
- **Gallery picks are copied into app-private storage.** The picker's own `content://` grant is
  short-lived and cannot be persisted, so the bytes are copied at the moment of the pick and only
  that path is stored. Removing media deletes the file.
- **The profile picture is deleted with the rest of the app data.** It is a file on disk, not a row
  in the database, so "erase all app data" deletes it explicitly rather than leaving a copy behind.
- **The shipped catalog is not a trust boundary for user data.** Editing a library row writes through
  the same path as any other edit; it cannot read anything the app cannot already read.
- **Backups are excluded.** Android backup rules keep app data out of cloud backup and device
  transfer, so a restored image cannot leak another person's history onto a new phone.

---

## Verifying a build

Release APKs are attached to the
[releases page](https://github.com/JuggyBob/IronPulse/releases). Each release publishes its own
checksums.

```bash
sha256sum IronPulse4.3.0-release.apk
```

Compare against the checksum in the release notes before installing an APK you did not build
yourself.
