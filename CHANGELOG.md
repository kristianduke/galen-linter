# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [0.2.0]

Spec arguments are now parsed individually rather than as one opaque blob, which is what makes
keyword highlighting and argument validation possible. Navigation lands in 0.3.0.

### Added

- **Code folding** for sections, `@objects`, `@groups`, `@set`, `@script`, `@on`, `@if`/`@elseif`/
  `@else`, `@for`/`@forEach`, `@rule`, rule invocations with a body, object statements and nested
  object definitions. A header with nothing under it does not offer a fold arrow.
- **Per-spec argument parsing** for all 21 specs, mirroring Galen's own processor grouping. Object
  names, ranges, units, sides, matchers, text operations, alignment keywords, corners, image
  options and filters, colour entries and file paths are now distinct nodes.
- **Contextual keyword highlighting.** Almost every Galen keyword is a bare word, and several are
  reused in unrelated roles, so colouring is driven from the parse tree. A misspelled keyword
  simply fails to light up. Special objects (`screen`, `viewport`, `parent`, `self`, `global`) get
  their own colour, and all of it is configurable under Editor | Color Scheme | Galen Spec.
- **Validation, split by whether Galen itself would reject the file.**
  - Errors (GL301, GL302, GL303, GL309, GL318, GL319, GL320, GL322, GL323): unknown spec name,
    `aligned` with a missing or mismatched edge, `absent` contradicting a positional spec, `count`
    given a `px` unit, `near` with no side, an invalid side, an unknown alignment direction, `on`
    without `edge`, and a corner combining two opposite sides.
  - Warnings (GL305, GL306, GL310, GL311, GL312, GL313, GL315, GL316): an invalid Java regex, an
    unknown text operation, a contrast level outside 0–258, `denoise` outside `map-filter`, an
    `image` with no sample, unknown image options, an unknown relative property, and a duplicated
    spec.
  - Most carry a "did you mean" suggestion.
- Object declarations, locator types and statement file paths are now distinct nodes, ready for the
  references arriving in 0.3.0.

### Notes

- Arguments supplied by `${...}` are never validated — their value is only known at run time.
- Two examples in Galen's official documentation are invalid Galen: `aligned horizontally screen`
  omits the required edge, and `near user-pic 10px` omits the required side. Both are now pinned by
  tests rather than reproduced.
- Test suite grew from 34 to 73.

## [0.1.0]

First release. Milestone 1: lexer, parser, PSI and syntax-level inspections.

### Added

- `.gspec` file type, language registration and `#` line-comment support.
- Restartable, line-local lexer. Balanced-brace scanning means `${ {a:1}.a }` and
  `${ x["}"] }` are read as single expressions rather than truncated at the first `}`.
- Recursive-descent parser mirroring Galen's own two-phase model: block structure from
  indentation first (tab = 4 columns, blank and comment lines skipped, siblings must match
  exactly), then per-line dispatch on the first token.
- Per-line error recovery, so one malformed line never blanks out the rest of the file.
- Syntax highlighting, plus a PSI-driven annotator for constructs the lexer cannot identify
  on its own (spec names, locators, object references) and a colour settings page.
- Inspections: GL001 mixed tab/space indentation, GL002 inconsistent indent step,
  GL003 trailing whitespace, GL005 object definition silently parsed as a comment,
  GL006 missing final newline.
- Parser diagnostics GL101 (unknown `@` statement), GL103 (dangling `@elseif`/`@else`),
  GL104 (inconsistent indentation), GL105, GL107 and GL109.
- `docs/galen-spec-reference.md`: a full implementation reference for the Galen spec
  language, with every claim tagged as documented, source-confirmed, inferred or unknown.

### Notes

- Recognises `@lib`, a real statement that appears nowhere in Galen's official documentation.
- Spec argument grammars are parsed as an opaque argument list for now; per-spec validation
  (the GL3xx rules) arrives with milestone 3.

[Unreleased]: https://github.com/kristianduke/galen-linter/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/kristianduke/galen-linter/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/kristianduke/galen-linter/releases/tag/v0.1.0
