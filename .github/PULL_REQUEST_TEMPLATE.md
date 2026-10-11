# Pull Request

## What does this change?

<!-- A clear, specific title. One logical change per PR. -->

## Type of change

- [ ] 🐛 **Bug fix** — non-breaking change that fixes a bug
- [ ] ✨ **New feature** — non-breaking change that adds functionality
- [ ] 💥 **Breaking change** — existing behaviour changes
- [ ] ♻️ **Refactor** — no functional change
- [ ] 📚 **Documentation** — docs only
- [ ] 🔧 **Build / CI / tooling**
- [ ] 🔒 **Security fix**

## Related issue

<!-- Closes #123, or Fixes #123. Leave blank if there is none. -->

Closes #

---

## Checklist

- [ ] The code compiles (`./gradlew assembleDebug`)
- [ ] All tests pass (`./gradlew testDebugUnitTest`)
- [ ] I added or updated a test that covers this change
- [ ] Android Lint produces no new warnings
- [ ] I ran the app on a device or emulator and checked the change works
- [ ] `README.md` updated, if user-visible behaviour changed
- [ ] `CHANGELOG.md` updated, under `[Unreleased]`

### If this is a bug fix

- [ ] I added a test named after the bug, that fails without this fix
- [ ] The test is in `app/src/test/java/com/example/` and runs on the JVM (no device needed)

---

## How this was implemented

<!--
Explain the approach and, more importantly, **why**. If you worked around something that looks
like a bug, say so and explain why that is the right call — a future contributor who cannot tell
why a line exists will eventually delete it and reintroduce the bug.
-->

## Alternatives considered

<!-- Anything you tried that did not work, and why you rejected it. -->

## Anything else?

<!-- Screenshots, before/after comparisons, or context a reviewer would otherwise be missing. -->

---

> 🔒 **If this PR touches anything security-sensitive** (authentication, secrets, the media
> picker, AI data sharing, or backup rules), say so here explicitly and request a review from the
> maintainer. Do not open a separate advisory in the same PR.