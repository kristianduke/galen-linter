# GalenLinter

[![Build](https://github.com/kristianduke/galen-linter/actions/workflows/build.yml/badge.svg)](https://github.com/kristianduke/galen-linter/actions/workflows/build.yml)

IntelliJ IDEA support for **Galen Framework** page specs (`.gspec`), targeting the Galen spec
language v2.x.

Galen's own tooling is frozen — the last release was 2.4 and the documentation site is dated
2017 — and it ships no editor support. Writing a `.gspec` today means no highlighting, no
navigation, and no feedback until a full Selenium run fails. This plugin moves those errors to
edit time.

## What it does today

- **Syntax highlighting**, including the parts a lexer cannot identify on its own. Spec names,
  locators and object references are all bare words, so they are coloured from the parse tree.
- **A real parser**, not a regex highlighter. It mirrors Galen's own two-phase model: block
  structure comes from indentation first, then each line is dispatched on its first token.
- **Per-line error recovery**, so one malformed line never blanks out the rest of the file.
- **Inspections** for the mistakes the language makes easy to hide:

  | Rule | Reports |
  |---|---|
  | GL001 | Indentation mixing tabs and spaces |
  | GL002 | Inconsistent indent step within a file |
  | GL003 | Trailing whitespace |
  | GL005 | An object definition silently parsed as a comment |
  | GL006 | Missing final newline |
  | GL101 | Unknown `@` statement |
  | GL103 | `@elseif` / `@else` with no matching `@if` |
  | GL104 | Inconsistent indentation between siblings |
  | GL105 / GL107 / GL109 | Missing `:`, unclosed section header, malformed object definition |

**GL005** is the one worth knowing about. Galen decides a line is a comment by testing whether
its *trimmed* text starts with `#`, before any structural parsing. So inside `@objects`:

```galen
@objects
    #footer   div.footer
```

defines nothing at all. Every later reference to `footer` fails at run time with no syntax error
pointing at the cause.

## Installing

Download `galen-linter-<version>.zip` from [Releases](https://github.com/kristianduke/galen-linter/releases)
and use **Settings | Plugins | gear | Install Plugin from Disk**.

To get update notifications instead, add this once under
**Settings | Plugins | gear | Manage Plugin Repositories**:

```
https://github.com/kristianduke/galen-linter/releases/latest/download/updatePlugins.xml
```

GitHub redirects `latest` to whichever release is newest, so the URL never needs changing.

## Building

Requires JDK 21.

```bash
./gradlew build        # compile and test
./gradlew buildPlugin  # -> build/distributions/galen-linter-<version>.zip
./gradlew runIde       # sandbox IDE with the plugin loaded
```

## Releasing

`gradle.properties` holds `pluginVersion` and is the single source of truth — the plugin
descriptor, the archive name and `updatePlugins.xml` are all derived from it.

1. Bump `pluginVersion` in `gradle.properties` and add a `CHANGELOG.md` entry.
2. Commit, then tag: `git tag v0.2.0 && git push origin v0.2.0`.

The tag triggers [`release.yml`](.github/workflows/release.yml), which builds the plugin and
publishes a GitHub release carrying the zip and an `updatePlugins.xml` whose download URL points
at that release's asset. `workflow_dispatch` with a version input does the same thing manually.

## Repository layout

| Path | Contents |
|---|---|
| `src/main/kotlin/com/galenlinter/lexer` | Hand-written, restartable lexer |
| `src/main/kotlin/com/galenlinter/parser` | Recursive-descent `PsiParser` |
| `src/main/kotlin/com/galenlinter/highlight` | Syntax highlighter, annotator, colour settings |
| `src/main/kotlin/com/galenlinter/inspections` | Inspection implementations |
| `src/test/testData/parsing` | Fixtures and golden-file PSI dumps |
| `docs/galen-spec-reference.md` | Full implementation reference for the spec language |

`docs/galen-spec-reference.md` is the working specification this plugin is built against: a
complete grammar, per-spec argument reference, and the planned GL0xx–GL7xx rule catalogue. Every
claim is tagged as documented, confirmed from Galen's source, inferred, or still unknown — the
official guide is silent or self-contradictory in several places, and the tags record which.

The test suite harvests all Galen examples out of that document and asserts each parses cleanly,
so the parser and the documentation cannot quietly drift apart.

## Licence

[MIT](LICENSE)
