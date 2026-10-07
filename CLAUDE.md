# CLAUDE.md

Operating manual for this repo. Read this before changing the write path.

## What this is

An Android share target: any app's share sheet (or its "process text" toolbar) can hand
us a highlight or a whole note; we clean it, render it into a quote, and append it to a
named note in an Obsidian vault via `obsidian://`.

The design exists to serve one real workflow: sharing a highlight from Kindle and having
it land under `## Quotes worth keeping` in the right book note, without typing anything.

## Build

There is **no `java`, `gradle`, or `adb` on `PATH`** on this machine. Use absolute paths.

```bash
export JAVA_HOME="D:/DevEnv/AndroidStudioMy/jbr"          # Android Studio's bundled JBR 21
./gradlew :app:testDebugUnitTest                          # 40 pure-logic tests, no device needed
./gradlew :app:assembleDebug
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
```

Version matrix is pinned to what is already in `~/.gradle/caches` on this machine, so a
clean build downloads nothing new: **AGP 8.2.2, Kotlin 1.9.22, KSP 1.9.22-1.0.17,
Compose compiler 1.5.10, Compose BOM 2024.02.00, compileSdk/targetSdk 34, minSdk 26.**

The Gradle wrapper points at the Tencent mirror, not `services.gradle.org`.

AGP 8.2.2 caps `compileSdk` at 34. Raising it means raising AGP.

Installing over USB on this HyperOS device needs the on-screen confirmation accepted;
`INSTALL_FAILED_USER_RESTRICTED` means the prompt was missed, not that the APK is bad.

## The Obsidian write path

Both were measured on the device (Android 16, HyperOS, Obsidian + Advanced URI plugin).
None of this is in Obsidian's documentation.

**`obsidian://new`** (built in)
- Auto-creates missing parent directories.
- `append=true` is **mandatory**. Without it, writing to a file that already exists is a
  **silent no-op** — no error, no change.
- `append=true` on a missing file creates it, so it is a natural upsert.
- Inserts its own `\n\n` before the appended content, so the payload must not start with
  a blank line.
- Has **no** heading parameter, and does **not** fire `x-success` on mobile.

**`obsidian://adv-uri`** (Advanced URI plugin)
- `heading=<name>&mode=append` appends at the end of that section — before whatever
  section follows. This is the only way to hit `## Quotes worth keeping`.
- **If the heading does not exist, it writes nothing and reports nothing.** `x-error`
  does not fire either. This failure is undetectable, so it must be *prevented*: see the
  skeleton action.
- `separator=` controls what is inserted before the payload (default a single `\n`; we
  pass `\n\n` so both paths separate entries identically).
- `clipboard=true` makes it read the payload off the clipboard instead of the URI.
- Fires `x-success` on mobile — but **also fires it on the missing-heading no-op**, so it
  proves only "the plugin ran", never "the write happened".

## Rules that are easy to break

- **Never** edit vault files with shell tools, and never write to a vault path directly:
  no `mv`, `rm`, or direct file writes in the vault. All writes go through `obsidian://`
  so Obsidian keeps its own links and index consistent.
- **A payload that is a hard-wrapped paragraph and one that is deliberately line-broken
  are indistinguishable.** `unwrapLines` is switchable and refuses to touch anything
  list- or heading-shaped for that reason. Do not make it unconditional.
- **Source-profile rules must demand an explicit marker** (share-chrome phrase, leading
  attribution dash, label-colon-link). A rule as loose as "line contains amazon" silently
  eats a highlight *about* Amazon, which is worse than under-cleaning.
- The `## Quotes worth keeping` heading is a **vault convention**, matching
  `Templates/Book.md` and `Templates/Reading Note.md` in the vault. Changing the default
  makes new writes land in a section the vault's live views do not read.

## Layout

```
domain/     pure Kotlin, no Android — the whole pipeline, covered by JVM tests
  Cleanup        the ordered stages that turn shared text into quote text
  Template       {placeholder} rendering, with empty-value pruning
  ShareParser    reads the book identity out of a share preamble
  ObsidianUri    builds both URIs; percent-encoding rules live here
  SourceProfile  per-app strip rules (Kindle + a generic default)
send/       ObsidianSender: dispatch, clipboard fallback above 16k chars, failure kinds
data/       Room (books/targets/history/outbox) + DataStore (settings)
ui/         Compose: ShareScreen, SettingsScreen
```

`domain/` deliberately has no Android dependency. Keep it that way — it is why the
pipeline is testable without a device.

## Known gaps

- The outbox has storage and a retry button, but no dedicated history screen yet.
- No Direct Share shortcuts (static `shortcuts.xml`) yet.
- Profiles are the two built-ins; user-defined profiles and a per-app mapping are not built.
- `x-success` is used only to return the user to the source app; it is not treated as a
  write receipt, for the reason above.
