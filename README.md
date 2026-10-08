# 拾曜Obsiclip

> **English** · [中文](README.zh.md)

An Android share target for **any app**: share a highlight or a whole note, and it is
cleaned, run through a template, and appended to the note you chose in your Obsidian vault —
in the section you chose within it.

Anything that can share text works: reading apps (Readest, Kindle, 微信读书), note apps,
browsers, chat. So does the「Process text」item in the text-selection toolbar, which covers
text you *selected* rather than shared. Obsidian's own share target can only drop a new
note in a vault; this decides *which* note, *which* section, and *what shape*.

**Kindle is the one app that needed extra work**, because it refuses to share or copy a long
passage at all — see [the clipboard routes](#when-a-share-sheet-refuses-common-with-kindle)
and [reading its notebook screen](#experimental-importing-from-kindles-notebook-screen).
Those are additions for one app's restrictions, not the shape of the rest.

The interface is **English and 简体中文** — it follows the device language, and
**Settings → Language** overrides that. This file is [also available in Chinese](README.zh.md);
the link at the top switches.

## What it does

Reading apps wrap a highlight in their own boilerplate: a share preamble, a store link, a
"read more" line. The app:

1. **Strips the wrapping** — the share preamble, the store link, any line that is nothing
   but a URL, and "label + link" footers. Only the quote survives.
2. **Rejoins hard-wrapped lines** — sentences broken by EPUB/PDF line width come back
   together, with no space for CJK and a space for Latin scripts, de-hyphenating words that
   were split across lines.
3. **Renders a template and previews it** — `> 「僕にはまだ、お母さんが必要なんだよ。」`
   plus a source line `> — 平野啓一郎《本心》 #reading`, both editable before you send.
4. **Writes where you chose** — through `obsidian://`, so Obsidian does the writing and its
   own links and index stay consistent.

**The book is never guessed.** Reading a title out of the share text looks clever right up
until it misreads one, and files a highlight into a brand new note beside the one it
belongs in. So destination and book are chosen separately: describe a book once (title,
author, year) and let the destination decide where it goes.

## Use

Share text from any app, or pick this app from the「Process text」entry in the text-selection
toolbar. From the top:

- **Write to Obsidian** — the send button sits first, under the app bar. The text has
  usually already arrived processed, so sending should never require a scroll.
- **Where it goes** — a row of chips. A path may contain `{title}` and friends, so one
  "book notes" destination serves every book. Shows the resolved path and section.
- **What you're reading** — a second row. Title, author and year only; it decides nothing
  about where the words land.
- Both rows, and both lists in settings, **reorder by long-press and drag**, and they share
  one order.
- **What will be written** — editable; your edit sticks until you change something else.
- **Cleaning rules** — at the bottom. Write mode lives in settings; it is a set-once choice.

### When a share sheet refuses (common with Kindle)

Kindle's own share refuses a long selection. Three ways around it, in order of convenience:

1. **Quick Settings tile** — pull down the shade, tap Obsiclip, and the clipboard lands in
   the app.
2. **Read on open** — opening the app from the launcher adopts the clipboard (can be turned off).
3. **「Read clipboard」** by hand.

All three are: select in the reading app → **Copy** → come back. Kindle's Copy is also
cleaner than its share — no preamble and no store link, so there is less to strip.
(Android 10+ only serves the clipboard to the focused app, which is why a tap is required.)

### Direct Share chips

Saved destinations appear as **Direct Share targets** in the share sheet. Tapping one writes
straight through **without opening this app at all** (it reuses the last book you chose).

### Experimental: importing from Kindle's notebook screen

Kindle forbids selecting a long passage in the reading view, so sharing one and copying one
both fail. The notebook screen is different: **each highlight sits in the accessibility tree
as plain text**, so reading it sidesteps the restriction — no selection, no clipboard.

**Settings → Experimental → Start collecting** → open the notebook page in Kindle → it
scrolls to the end by itself and reads it → open the review from the notification or from
settings.

- **Needs the accessibility permission**, which only you can grant, in system settings. The
  service is restricted to Kindle, so it cannot observe any other app.
- **Review first by default.** Kindle collapses long highlights in that list, and a
  collapsed one looks exactly like a short one — silent truncation is the worst failure
  here, so anything that ends like a truncation, or is already in the note, is left un-ticked.
- **Experimental.** The heuristics read screen structure, so a Kindle redesign may break it.

## Writing to Obsidian

Two paths, both measured on a real device:

| | Advanced URI | Built-in URI |
| --- | --- | --- |
| Needs a plugin | Yes (Advanced URI) | No |
| Can target a section | ✅ | ❌ appends to end of file |
| Long content | clipboard fallback | clipboard fallback |

Advanced URI is the default. **Without the plugin, switch to the built-in URI in settings.**

> ⚠️ Two things the documentation does not say, both measured:
> - The built-in URI **without `append=true` is a silent no-op on an existing file** — no
>   error, no change.
> - Advanced URI **does nothing, and reports nothing, when the target section is missing**.
> - So a brand new book needs its note skeleton created first, or the write quietly fails.

## Settings

Sections fold away behind their titles, all collapsed by default.

- **Destinations** — path, section, output format. The default destination is chosen here.
  Paths are templates, so one destination serves every book.
- **Books** — title / author / year, and nothing about where they go.
- **Output formats** — a default template plus any number of named presets a destination can
  pick. Precedence: the destination's format, then the rule set's own template, then the
  default.
- **Write mode** — Advanced / built-in URI, read clipboard on open, return to the source app.
- **Cleaning pipeline** — every stage independently switchable, plus a default rule set.
- **Custom rules** — write your own regexes. **A rule that does not compile is named in the
  editor**, rather than silently doing nothing: a regex that never matches looks exactly
  like one that works.
- **Per-app rules** — pin a source app to a rule set. Precedence: pinned, then package match,
  then default.
- **Language** — Follow system, English, or 简体中文. Takes effect immediately; a capture in
  progress survives the switch.
- **History** — what was sent, where it landed, whether it worked, and a re-send.

> **A write leaves you on the note.** Obsidian is always pulled forward — a URI can only be
> handled by starting the app — so there is no way to write without it appearing. To go back
> where you came from, turn on "return to the source app": it uses Obsidian's `x-success`
> callback, which fires once the write is done.

## Build

```bash
export JAVA_HOME="/path/to/Android Studio/jbr"
./gradlew :app:testDebugUnitTest     # 42 pure-logic tests, no device needed
./gradlew :app:assembleDebug
```

Pinned to AGP 8.2.2 / Kotlin 1.9.22 / Compose 1.5.10 / compileSdk 34 / minSdk 26.

## License

[GNU AGPL-3.0](LICENSE). Use it, change it, share it — anyone distributing it, or running a
modified version as a network service, must publish their source under the same terms.
