# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [0.12.2]

### Fixed

- **GL304's anchoring now reaches `near`, `inside` and `on`.** Those specs parse their ranges inside
  side groups, and the side-group parser absorbs the stray `to` as though it were a side keyword —
  so a search restricted to the top level never saw it. `inside container 10px to 20px left`
  underlined the `to` and reported it as an invalid side: the right token, with the wrong
  explanation and a "did you mean" that would have made things worse. It now underlines the
  misplaced `px`, like every other spec.
- A `to` in side position is no longer reported as a bad side keyword, since it always means a
  malformed range.

### Notes

- The anchoring is positional, not a list of known typos: whatever sits between the first bound and
  the `to` is the mistake, whether or not it resembles a unit. A table-driven test now pins that
  across `p`, `pxx`, `pixels`, `em`, `%` and a correctly-spelled but misplaced `px`, over the flat
  specs, the object-and-range specs and the side-group specs.
- Test suite grew from 297 to 303.

## [0.12.1]

### Fixed

- **GL304 now anchors on a malformed unit too.** `10px to 15px` already underlined the offending
  `px`, but `10p to 15px` fell back to underlining the `to`: a malformed unit never parses into the
  range, so there was no unit node to find. The check now also looks for a token stranded between
  the first bound and the `to`.
- **Two quick fixes were offered but did nothing when clicked.** `ProblemDescriptor.textRangeInElement`
  is null whenever a problem is registered without an explicit range, and the fix returned early on
  null instead of treating the whole element as the target. This affected "Remove the unit from the
  first bound" and "Remove 'is'".

### Notes

- Both fixes now have tests that apply the fix and assert the resulting document text, rather than
  only asserting that the fix was offered — which is precisely what let the no-op ship twice.
- Test suite grew from 294 to 297.

## [0.12.0]

Corrections, all from feedback. Two of the rules added in 0.11.0 were simply wrong about what Galen
permits, which is worse than not having them.

### Fixed

- **`left-of button` is valid and is no longer reported.** `SpecWithObjectAndRangeProcessor` reads a
  range only if there is more input and otherwise defaults to `>= 0`, so `above`, `below`,
  `left-of` and `right-of` need no range at all. Only `width` and `height` genuinely require one.
- **A range with a comparison operator needs no unit.** `ExpectRange` returns early for `> 40`,
  `< 40`, `>= 40` and `~ 100`, so those were being reported wrongly. A bare exact range and a `to`
  range do still require the unit, and Galen throws without it — so those stay errors rather than
  becoming warnings.
- **GL304 now underlines the offending unit**, not the `to` that merely reveals it. For
  `height 400px to 800px` the mistake is the first `px`, and there is now a fix to remove it.
- **Section headers and `${...}` expressions no longer share a colour.** Making sections visible in
  0.6.0 moved them onto METADATA, which is what expressions already used. Object statement headers
  and ordinary object references had likewise resolved to the same purple, differing only by
  italics. Both pairs are now separated per scheme.

### Notes

- The palette is now checked by asking the live colour scheme what each key actually paints, both
  that nothing is invisible and that constructs a reader must tell apart do not share ink. That is
  how these two collisions were found.
- Test suite grew from 284 to 294.

## [0.11.0]

All from feedback on real use. Every one of these parsed cleanly before, which was the problem —
Galen either rejects them at run time or, worse, quietly reads them as something else.

### Added

- **GL106** an unterminated string. `text is "comprehensive` looked entirely ordinary: the lexer
  stops such a string at the end of its line so the following lines still parse, and nothing
  noticed.
- **GL304** the range grammar, covering three shapes that come up repeatedly:
  - `height is 400 to 800px` — a matcher where a range belongs; these specs take the range directly;
  - `width 154 to 164p` — a mistyped unit, which leaves the range with no unit at all, with a fix
    to correct it;
  - `height 400px to 800px` — a unit on the first bound, which ends the range early and strands the
    rest of the line.
- **GL324** arguments given to a spec that takes none. `visible matches 10px` runs as plain
  `visible`, silently discarding the rest.
- **GL702** now also covers an unterminated block comment in embedded JavaScript.

### Changed

- **Embedded JavaScript has its own colour palette.** Borrowing Galen's colour keys made the two
  languages indistinguishable where they meet — JS keywords came out the same orange as Galen spec
  names, JS strings the same green as locators, the API colour the same purple as object
  references. The JavaScript palette is now defined per scheme, in hues chosen to sit outside
  Galen's, and identifiers and punctuation are coloured too so the whole span reads as one language.

### Notes

- `count` ranges are exempt from the unit check: theirs never carry one.
- Ranges supplied by `${...}` are exempt, as everywhere else.
- Test suite grew from 268 to 284.

## [0.10.0]

### Added

- **Reformat Code.** Indentation is normalised to a consistent step, and locator columns inside an
  `@objects` block are lined up the way they are written by hand.
- Code style settings for Galen, defaulting to four spaces — what every example in Galen's own
  documentation uses, and the width Galen itself assigns to a tab.

### Notes

- **Indentation is the syntax here**, so a formatter that gets a level wrong does not misalign a
  file, it changes what the file means. The indent of each line is therefore reproduced from the
  depth the parser already assigned it, never re-derived from the text. A property test asserts
  that reformatting leaves the parse structure byte-identical across every test fixture and every
  example in the reference documentation.
- The platform's own formatter cannot do this job: it rewrites whitespace between blocks, and
  Galen's indentation is a token the parser depends on rather than whitespace. The work is done by
  a post-format processor instead.
- `@script` bodies are left alone. That indentation is JavaScript's, and the author's to arrange;
  Galen only requires it to be deeper than the `@script` line.
- Test suite grew from 258 to 268.

## [0.9.0]

Custom rules were the last substantial part of the language with no checking at all. An invocation
matching no rule simply does nothing, silently, and a rule's text can drift away from its call sites
with no signal.

### Added

- **GL601** an invocation matching more than one rule. Galen picks one and the spec cannot show
  which; an undecorated `%{param}` matches `.*`, so this is easy to cause by accident.
- **GL602** an invocation matching no rule. Names the closest declared rule when there is one.
- **GL603** `@ruleBody` outside a `@rule` declaration.
- **GL604** an invocation with an indented block whose rule never invokes `@ruleBody`, so the block
  is silently ignored.
- **GL606** a `%{name: regex}` capture whose regular expression does not compile.
- **GL607** a rule body using a `${...}` the rule does not declare.

### Notes

- Rules are collected from the file, its transitive `@import`s, **and** JavaScript files loaded with
  `@script`, by scanning them for `rule("...")`. Without the last of those, every invocation of a
  JavaScript-defined rule would be reported as matching nothing — unusable in exactly the projects
  that lean on rules most.
- GL607 ignores anything being *called*, which covers Galen's API and any function a `@script` file
  defines without needing to enumerate them. `objectName`, `@set` variables and loop bindings are
  accepted too.
- Rule text is compiled to a pattern with literal parts escaped, so regex metacharacters occurring
  naturally in rule wording cannot change what it matches.
- Test suite grew from 240 to 258.

## [0.8.0]

Navigation and presentation. Galen nests by indentation with no closing delimiters, so once a block
runs past a screenful nothing on screen says where you are — most of this addresses that.

### Added

- **Go to Symbol** (Ctrl+Alt+Shift+N) over every object in the project, answered from the existing
  declaration index, so names are enumerated without opening or parsing anything.
- **Structure view** — an outline of sections, `@objects` entries, object statements, rules and
  control flow. Spec lines are deliberately omitted; they would swamp it.
- **Breadcrumbs** showing the enclosing blocks, e.g. `= Main section = › hero-header: › width 100px`.
- **Colour swatches** in the gutter for `color-scheme` values, editable with the colour picker.
  Named colours and both 3- and 6-digit hex are recognised.
- **Image preview on hover** for `image file` samples. For a framework whose purpose is comparing
  rendered pixels against a reference, seeing the reference is the whole question.

### Notes

- A gradient such as `#000-#555-#955` gets no swatch: it is several colours in one token, with no
  single value to show or set, and representing it by its first stop would be misleading.
- Test suite grew from 228 to 240.

## [0.7.0]

### Added

- **Embedded JavaScript now works without a JavaScript plugin.** IntelliJ IDEA Community bundles no
  JavaScript support, so the injection added in 0.6.0 could not run there and `${...}` expressions
  and `@script` blocks stayed one flat, unchecked span. A small in-house tokenizer now colours the
  lexical surface — comments, strings, numbers, keywords, and Galen's own API functions
  (`count`, `find`, `findAll`, `isVisible`, `isPresent`, `viewport`, `screen`) — and checks it.
- Inspections on embedded JavaScript:
  - **GL701** `.name` called as a method. It is the one page-element member that is a property, so
    `find("x").name()` throws at run time.
  - **GL702** an unterminated string literal.
  - **GL703** a near-miss of a Galen API function, such as `isVisble`. Only when the name is called
    and differs by a single character, since a `@script` file may define anything.
  - **GL704** an unbalanced bracket — checked only inside a `${...}` expression, which must be
    self-contained. A `@script` body is one program across several lines, so a brace opened on one
    line is legitimately closed on another.
- The five JavaScript colours are configurable under Editor | Color Scheme | Galen Spec.

### Notes

- The fallback stands down entirely when a real JavaScript plugin is installed, so the two never
  compete: with Ultimate or WebStorm you still get full injection.
- Deliberately no scope or type analysis — no undefined-variable or argument-count checks. Without
  a parser those cannot be judged reliably, and a false positive on working JavaScript is worse
  than a missed one.
- Test suite grew from 211 to 228.

## [0.6.0]

### Added

- **Optional JavaScript support.** When a JavaScript plugin is installed (IntelliJ IDEA Ultimate,
  WebStorm), Galen's embedded JavaScript is injected and gets real JS highlighting, completion and
  inspections — both `${...}` expressions and `@script` blocks, the latter injected as a single
  fragment so a function spanning several lines is analysed as one piece. Declared as an optional
  dependency, so IDEA Community, which bundles no JavaScript support, is unaffected.

### Fixed

- **Find Usages now finds the members of a wildcard family.** A family declared `row-value-*` is
  used through `row-value-1`, `row-value-2` and so on, which share no searchable text with the
  declaration — so the platform's text-driven search returned nothing, even though every one of
  those references resolves to it. Resolution and navigation were always correct; only the search
  was blind.
- **Section headers `= Like This =` are coloured.** They fell back to `CLASS_NAME`, which has no
  entry at all in the bundled colour schemes and therefore renders as plain text.
- Three further colours had the same defect and were silently invisible: special objects
  (`screen`, `viewport`), the `%` warning prefix, and — in the light scheme only — object statement
  headers. All now use keys the bundled schemes actually paint.

### Notes

- A test now asks the real colour scheme whether every Galen colour key resolves to something
  visible, so this class of bug cannot recur unnoticed. It is invisible to every other kind of
  test: the element is parsed, annotated and "coloured", just with nothing to show for it.
- Test suite grew from 193 to 211.

## [0.5.0]

### Added

- **Documentation on hover** and in Quick Documentation (Ctrl+Q), for every spec, every statement,
  and the keyword vocabularies — sides, matchers, text operations, alignment keywords, count
  filters, locator types, special objects, units and image options. Each entry gives what the
  construct does, its syntax, its accepted values and an example.
- The documentation records behaviour Galen's own guide gets wrong or omits: that `near` requires a
  side, that `aligned` requires an edge and which pairs are legal, that `on` requires the word
  `edge`, that a `count` range carries no unit, and that `@lib` exists at all.
- **Hovering an object name** shows its locator and the file that declares it, resolved across
  `@import`. A wildcard family explains itself — `menu_item-*` notes that Galen names the matches
  `menu_item-1`, `menu_item-2` and so on.

### Notes

- Verified that embedded JavaScript survives lexing intact: `${...}` is a single token even when it
  contains braces or quoted strings (`${ count("a-*") > 0 && data["}"] }`), `@script` bodies are
  kept raw and never produce Galen syntax errors, and `${...}` inside a quoted expectation is part
  of the string, matching Galen's own substitution. Pinned by tests.
- `${...}` is still coloured as one span. IntelliJ IDEA Community bundles no JavaScript support, so
  the contents cannot be injected and highlighted as JavaScript.
- Test suite grew from 170 to 193.

## [0.4.0]

Completion and quick fixes, plus two fixes for issues reported from using 0.2.0.

### Added

- **Context-aware completion.** What is offered depends on the position in the line's grammar, not
  just the word being typed:
  - statement keywords at the start of a line, spec names at the start of a spec line;
  - object names — from this file and everything it imports — wherever an object belongs, including
    dotted nested names and the special objects;
  - side keywords after a range, and nothing else, since nothing else is legal there;
  - `aligned` offers only the edges valid for the direction already chosen, so
    `aligned vertically` never suggests `top`;
  - matchers and text operations for `text`/`ocr`/`css`, filters for `count`, options and filters
    for `image`, locator types inside `@objects`, and group names after `&`.
- **Quick fixes.** Every "did you mean" suggestion is now applicable:
  - correct a misspelled spec name, side, text operation, relative property, image option or filter;
  - swap an alignment edge for one valid in that direction — all of them are offered, since which
    was meant is genuinely ambiguous;
  - correct an object or group name to a similar one in scope;
  - **add the missing `@import`** when the object is declared in a file this spec does not import,
    inserted after any existing imports;
  - remove trailing whitespace, add a final newline, normalise mixed indentation.

### Fixed

- **Renaming an object to a name containing a dash is no longer refused.** With no names validator
  registered, the platform falls back to Java identifier rules, which reject `-` — so renaming
  `hero-header` to anything with a dash failed as "not a valid identifier" despite Galen being
  perfectly happy with it. Galen names are now validated by the lexer's own rule: a name is valid
  when it lexes as a single word. A leading `#` is still rejected, since it would turn the
  declaration into a comment and silently delete the object.
- **A collapsed block no longer repeats its header.** The fold region started *after* the header
  line while the placeholder repeated it, so collapsing rendered
  `hero-header:hero-header: ...`. The region now covers the header text itself, starting after the
  indentation so a collapsed block keeps its place in the indentation structure.
- **Spec names are visibly coloured.** `visible`, `contains`, `width` and the rest were recognised
  and annotated correctly, but their colour key fell back to `FUNCTION_CALL`, which itself falls
  back to `IDENTIFIER` — plain default text in essentially every scheme. They now fall back to
  `KEYWORD`, which is what they are: the language's verbs.
- **Object statement headers are coloured, and distinctly.** `hero-header:` shares an element type
  with an ordinary object reference, so it is now told apart by position and given its own
  "Object statement header" colour. (Headers were uncoloured entirely before 0.3.0, which changed
  their element type.)

### Notes

- Colour choices are all configurable under Editor | Color Scheme | Galen Spec, now 30 named keys.
- Test suite grew from 118 to 170.

## [0.3.0]

Objects declared under `@objects` are now real symbols: ctrl+click, find usages and rename, across
files and through `@import`.

### Added

- **Go to declaration** from any object name — in a spec argument or an object statement header —
  to its `@objects` entry. Resolution handles dotted nested names (`search_panel.input`) and
  wildcard families, so `menu_item-3` finds its `menu_item-*` declaration and `item-#` matches only
  digits.
- **Cross-file resolution through `@import`**, transitively. `ImportProcessor` merges an imported
  file's objects into the importing spec, so imported names really are in scope. Import cycles
  terminate rather than recurse.
- **A file-based index** of object declarations, so navigation and find-usages stay fast as a
  project grows and work for files that are not open.
- **Find usages** and **rename**. Renaming an object updates its usages, including in files that
  import it.
- **Ctrl+click on file paths** in `@import`, `@script`, `component`, `image file` and
  `filter mask`, resolved relative to the containing file the way Galen does.
- **`&group` references** resolving to their `@groups` declaration, including the bracketed
  `(a, b) objects` form.
- **`${...}` variable navigation** to the declaring `@set` entry, `@for`/`@forEach` binding
  (including `next`, `prev` and `index`) or `%{...}` rule parameter. Loop and rule bindings are
  scoped to the construct that declares them.
- Inspections: GL201 unresolved object, GL202 unresolved group, GL501 missing file.

### Notes

- **Rename is refused for wildcard families.** Renaming `menu_item-*` cannot rewrite the
  `menu_item-3` usages it matches, and renaming only the declaration would silently break the file,
  so it fails with an explanation instead.
- Unresolved references are **warnings, not errors**: an `@import` may point at a classpath resource
  the IDE cannot see, and a JavaScript rule can add objects at run time via `addObjectSpecs`.
- When a name is declared in a file that is not imported, the message says so and names the file —
  a forgotten `@import` is the usual cause.
- Names built from `${...}` are never reported unresolved.
- Renaming a `${...}` variable is not supported: the identifier sits inside an opaque JavaScript
  expression that cannot be rewritten reliably.
- Test suite grew from 73 to 118.

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

[Unreleased]: https://github.com/kristianduke/galen-linter/compare/v0.12.2...HEAD
[0.12.2]: https://github.com/kristianduke/galen-linter/compare/v0.12.1...v0.12.2
[0.12.1]: https://github.com/kristianduke/galen-linter/compare/v0.12.0...v0.12.1
[0.12.0]: https://github.com/kristianduke/galen-linter/compare/v0.11.0...v0.12.0
[0.11.0]: https://github.com/kristianduke/galen-linter/compare/v0.10.0...v0.11.0
[0.10.0]: https://github.com/kristianduke/galen-linter/compare/v0.9.0...v0.10.0
[0.9.0]: https://github.com/kristianduke/galen-linter/compare/v0.8.0...v0.9.0
[0.8.0]: https://github.com/kristianduke/galen-linter/compare/v0.7.0...v0.8.0
[0.7.0]: https://github.com/kristianduke/galen-linter/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/kristianduke/galen-linter/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/kristianduke/galen-linter/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/kristianduke/galen-linter/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/kristianduke/galen-linter/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/kristianduke/galen-linter/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/kristianduke/galen-linter/releases/tag/v0.1.0
