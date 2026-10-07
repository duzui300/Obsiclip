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
./gradlew :app:testDebugUnitTest                          # 42 pure-logic tests, no device needed
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
  ObsidianUri    builds both URIs; percent-encoding rules live here
  SourceProfile  share-chrome rules (universal) plus Kindle's own
  UserProfile    compiles the rules a user wrote, naming the ones that do not parse
send/       ObsidianSender: dispatch, clipboard fallback above 16k chars, failure kinds
            ShareShortcuts: pushes saved targets as Direct Share targets
data/       Room (targets/books/formats/profiles/app_profiles/history/outbox) + DataStore
ui/         Compose: ShareScreen, SettingsScreen, AddTargetDialog, AddBookDialog,
            ProfileEditorDialog, SettingsFormats, SettingsProfiles, HistoryScreen,
            Reorderable, QuoteTileService
```

`domain/` deliberately has no Android dependency. Keep it that way — it is why the
pipeline is testable without a device.

**A capture is a target paired with a book.** Two axes, deliberately independent:

- **A target is where it goes** — path, section, output format. Its path is a *template*,
  so one `30-Reading/Book/{title}.md` target serves every book rather than needing a row
  each. That is what replaced a target per book.
- **A book is what is being read** — title, author, year, and nothing else. It fills
  `{title}`/`{author}`/`{year}` and decides nothing about where the words land.

Keeping these apart is the point: describing a book once should not also decide which file
it is filed in, and a destination should not have to be duplicated per title. The book name
is still never inferred from the share text — one misread title files a highlight into a
new note beside the one it belongs in.

**Output template precedence, most specific first:** the target's chosen format, then the
rule set's own template, then the default. The target wins because it knows the vault's
convention for that note; the rule set only knows the shape of what comes in.

**A write leaves the user on the note.** Obsidian is always pulled forward — a URI can
only be handled by starting the app — so there is no way to write without it appearing,
and a "silent write" setting could never mean what it sounded like. The `x-success`
callback is the way out: it fires once Obsidian has finished, and starting our activity is
what returns the user to the app that shared — see `isReturnCallback` in `MainActivity`.
`WriteRequest.silent` survives but is not a user setting: only skeleton creation uses it,
because that is setup rather than reading.

**Migrations are hand-written.** Room validates the live schema against the entities on
open, so a hand-written `CREATE TABLE` that differs by a column default crashes at
runtime. The two things that bite: a non-null column with no `@ColumnInfo(defaultValue)`
must be created without `DEFAULT`, and `ALTER TABLE ADD COLUMN` on a non-null column
*requires* one. Dropping a column needs a rebuild-copy-rename, because SQLite cannot drop
one on the oldest supported devices. Check on a device — a unit test cannot see this.

**The app outlives a write**, so anything still on screen when it is reopened is stale by
definition. That is why adopting the clipboard overwrites rather than refusing to replace
what is there. Note that re-entering from the launcher while alive arrives via
`onNewIntent`, not `onCreate` — the tile and the icon each need their own path.

## The Kindle collector

Kindle refuses to let a long passage be selected in the reading view, which is why sharing
one and copying one both fail. The notebook screen is the only place it leaves them
reachable: each highlight sits in the accessibility tree as plain text, so reading that tree
sidesteps the restriction rather than working around it.

Three things it cost to learn, all found by using it rather than by reading it:

- **Never read the window tree from `onAccessibilityEvent`.** That callback runs on the
  service's main thread and fires constantly, and a tree read is a blocking round trip that
  waits on the observed app — out to the full timeout when the window on top is not one the
  service may read. The log showed five-second gaps between events, and the whole
  accessibility pipeline stalled. Only the event's own fields belong in that callback; the
  tree work goes to a background thread, throttled.
- **"Switched on" is not "running".** Seen here: the enabled-services setting listed the
  service while `dumpsys accessibility` reported `Bound services:{}` — on paper only, with
  every arming silently doing nothing. Hence `CollectorStatus` with three states rather than
  a boolean: a boolean can only answer this wrongly, and telling the user to flip a switch
  that already looks on wasted the most time of anything in this feature.
- **A finished run is reachable only by notification** unless something else points at it.
  Missing the notification left the highlights in storage with nothing leading to them, so
  the settings section names the pending run too.

Two more, about intents: the collection notification's intent is an `ACTION_MAIN` with no
text, which is exactly what a launcher tap looks like — it has to be checked before the
launcher branch, or it is swallowed. And finishing an activity with nothing to go back to
drops the user on the launcher, so a write with no source app (clipboard, tile, batch) must
clear any remembered source and must only close if something actually opened.

Identification is structural, never by wording — Kindle's UI strings are localised. A quote
is the longest leaf text; the notebook is a scrollable list carrying several of them. The
Activity's class name starts a run, because that is code rather than text. A scroll that
refuses is the end of a list, not a failure to drive it.

## Known gaps

- The collector's heuristics are unverified against a short-highlight notebook: a quote
  shorter than the length floor would be missed silently, and the floor is a guess.
- No rule preview: the settings editor cannot replay a sample share through the rules.
- `x-success` is used only to return the user to the source app; it is not treated as a
  write receipt, for the reason above.
