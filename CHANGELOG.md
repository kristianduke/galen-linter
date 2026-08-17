# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

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

[Unreleased]: https://github.com/kristianduke/galen-linter/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/kristianduke/galen-linter/releases/tag/v0.1.0
