# GalenLinter

[![Build](https://github.com/kristianduke/galen-linter/actions/workflows/build.yml/badge.svg)](https://github.com/kristianduke/galen-linter/actions/workflows/build.yml)

IntelliJ IDEA support for **Galen Framework** page specs (`.gspec`), targeting the Galen spec
language v2.x.

Galen's own tooling is frozen — the last release was 2.4 and the documentation site is dated
2017 — and it ships no editor support. Writing a `.gspec` today means no highlighting, no
navigation, and no feedback until a full Selenium run fails. This plugin moves those errors to
edit time.

## What it does today

- **A real parser**, not a regex highlighter. It mirrors Galen's own two-phase model: block
  structure comes from indentation first, then each line is dispatched on its first token. Error
  recovery is per line, so one malformed line never blanks out the rest of the file.
- **Syntax highlighting**, including the parts a lexer cannot identify on its own. Spec names,
  sides, matchers, alignment keywords, locators and object references are all bare words — several
  reused in unrelated roles — so they are coloured from the parse tree. A misspelled keyword simply
  fails to light up.
- **Code folding** for sections, `@objects`, `@groups`, control flow, rules and object statements.
- **Navigation.** Ctrl+click an object name to reach its `@objects` entry, across files and through
  `@import`. Find usages and rename work too, including renaming usages in importing files.
  Wildcard families resolve: `menu_item-3` finds its `menu_item-*` declaration. File paths in
  `@import`, `component`, `image file` and friends are clickable, and `${...}` variables navigate
  to their `@set` entry, loop binding or rule parameter.
- **Finding your way around.** Go to Symbol over every object, a structure view outlining the
  file, breadcrumbs for the enclosing blocks, colour swatches on `color-scheme` values, and an
  image preview when you hover an `image file` sample.
- **Documentation on hover** for every spec, statement and keyword — what it does, its syntax and
  its accepted values. Hovering an object name shows its locator and which file declares it, across
  `@import`.
- **Completion** that knows where you are: spec names inside an object statement, object names
  (including from imported files) where an object belongs, side keywords after a range, and only
  the alignment edges valid for the direction you chose.
- **Quick fixes** for every suggestion — correct a misspelled keyword or object name, swap an
  invalid alignment edge, and add the `@import` that a referenced object needs.
- **Embedded JavaScript is highlighted and checked.** With a JavaScript plugin installed (IDEA
  Ultimate, WebStorm), `${...}` expressions and `@script` blocks get full JS support via language
  injection. Without one — IDEA Community bundles no JavaScript — a built-in fallback colours them
  and catches unbalanced brackets, unterminated strings, and the `.name()` trap.
- **Inspections** for the mistakes the language makes easy to hide:

  | Rule | Reports |
  |---|---|
  | GL001 / GL002 / GL003 / GL006 | Mixed tab-and-space indentation, inconsistent indent step, trailing whitespace, missing final newline |
  | GL005 | An object definition silently parsed as a comment |
  | GL101 / GL103 / GL104 / GL105 / GL107 / GL109 | Unknown `@` statement, dangling `@elseif`/`@else`, inconsistent sibling indentation, missing `:`, unclosed section header, malformed object definition |
  | GL201 / GL202 / GL501 | Unresolved object, unresolved group, missing file |
  | GL301 / GL302 / GL303 / GL309 / GL318 / GL319 / GL320 / GL322 / GL323 | Spec arguments Galen itself rejects — unknown spec, bad `aligned` direction or edge, `absent` contradicting a positional spec, `count` with a unit, `near` without a side, an invalid side, `on` without `edge`, a corner combining opposite sides |
  | GL305 / GL306 / GL310 / GL311 / GL312 / GL313 / GL315 / GL316 | Things Galen tolerates but you probably did not mean — an invalid Java regex, an unknown text operation, a bad contrast level, `denoise` outside `map-filter`, an `image` with no sample, unknown image options, an unknown relative property, a duplicated spec |

  Most carry a "did you mean" suggestion. Anything supplied by a `${...}` expression is never
  reported, since its value is only known at run time.

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
2. Commit, then tag: `git tag v0.4.1 && git push origin v0.4.1`.

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
