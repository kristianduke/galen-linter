# Galen Spec Language (v2.x) — Implementation Reference for a Linter

Source: <https://galenframework.com/docs/reference-galen-spec-language-guide/> (Galen Framework, docs © 2013–2017, last documented release **2.4**).

This document is the working reference for implementing **GalenLinter**. It restates the entire spec-language guide in a form that maps directly onto lexer / parser / semantic-analysis / lint-rule work, and it explicitly separates:

- **[D]** — documented behaviour, taken from the official guide.
- **[S]** — confirmed by reading Galen's own source (`galenframework/galen`, `galen-core/src/main/java/com/galenframework/`). Authoritative, and in several places it contradicts or extends the docs.
- **[I]** — inferred from examples; consistent with the docs but never stated outright.
- **[?]** — genuinely unspecified; a decision the linter must make, or a fact still to confirm against the reference parser.

Anything marked **[?]** must not be turned into a hard error without verification — emit it as a warning or make it configurable.

---

## Table of contents

1. [File model & lexical structure](#1-file-model--lexical-structure)
2. [Indentation and block structure](#2-indentation-and-block-structure)
3. [Top-level grammar (EBNF)](#3-top-level-grammar-ebnf)
4. [Object definitions — `@objects`](#4-object-definitions--objects)
5. [Object groups — `@groups`](#5-object-groups--groups)
6. [Object references and pattern matching](#6-object-references-and-pattern-matching)
7. [Special objects](#7-special-objects)
8. [Sections, tags — `= … =` and `@on`](#8-sections-tags-----and-on)
9. [Object statements and spec lines](#9-object-statements-and-spec-lines)
10. [Ranges — the universal value grammar](#10-ranges--the-universal-value-grammar)
11. [Spec reference — complete argument grammars](#11-spec-reference--complete-argument-grammars)
12. [Control flow: `@if`, `@for`, `@forEach`, `@die`](#12-control-flow-if-for-foreach-die)
13. [Variables, scripts, expressions — `@set`, `@script`, `${…}`](#13-variables-scripts-expressions--set-script-)
14. [Imports — `@import`](#14-imports--import)
15. [Custom rules — `@rule`, `|`, `@ruleBody`](#15-custom-rules--rule--rulebody)
16. [Galen Specs JS API surface](#16-galen-specs-js-api-surface)
17. [Name resolution & scoping model](#17-name-resolution--scoping-model)
18. [Keyword / token tables (implementation cheat-sheet)](#18-keyword--token-tables)
19. [Known documentation inconsistencies](#19-known-documentation-inconsistencies)
20. [Proposed lint rule catalogue](#20-proposed-lint-rule-catalogue)
21. [Suggested linter architecture](#21-suggested-linter-architecture)

---

## 1. File model & lexical structure

### 1.1 Files

| Aspect | Value |
|---|---|
| Page spec extension | `.gspec` **[D]** (used throughout the docs: `header.gspec`, `user-profile.gspec`) |
| Script extension | `.js` **[D]** |
| Encoding | **[?]** — not documented. Assume UTF-8; the docs contain non-ASCII prose only. |
| Line endings | **[?]** — accept `\n`, `\r\n`, `\r`. |

A spec file has **two conceptual parts**: *object definitions* and *object specs* **[D]**. In practice a file is a sequence of top-level statements in any order, though `@objects` conventionally comes first.

### 1.2 Comments

> All comments should start with `#` symbol in the **beginning of a line**. If you use `#` somewhere in the middle of text it will be taken as is. That is due to ability to work with CSS locators which might have this symbol. **[D]**

```galen
# This line a comment
# However next line is not a comment
object  css     #container ul li
```

**Implementation consequence:** the comment token is *not* a general `#…EOL` rule. A line is a comment iff its **first non-whitespace character** is `#` **[S]** — `IndentationStructureParser` tests the *trimmed* line, so leading indentation before `#` is explicitly legal. Comment lines are dropped **before** any structural parsing, exactly like blank lines.

This creates two genuine hazards a linter should warn about:

- An object named with a leading `#` inside `@objects` (e.g. `#footer   div.footer`) silently becomes a comment.
- A CSS locator placed at the start of a continuation-style line would be swallowed.

There is **no block-comment syntax** and **no trailing/inline comment syntax** **[D — by omission]**. Text after a valid statement on the same line is part of the statement.

### 1.3 Whitespace

- Fields within a line are separated by runs of spaces/tabs; the docs align columns with spaces for readability, but any run of whitespace is a separator **[I]**.
- Tabs vs. spaces for *indentation*: **[?]**. Recommend the linter (a) treats a tab as an implementation-defined width, and (b) emits a warning on mixed tabs/spaces in one file.
- Trailing whitespace is insignificant **[I]** — note the docs contain examples with trailing spaces (`= Main section = ` , `@objects `).

### 1.4 Significant characters

| Char | Meaning | Context |
|---|---|---|
| `#` | comment (line-initial); digit-wildcard in object patterns; CSS id in locators; hex colour prefix | multiple |
| `@` | statement keyword prefix; object-correction prefix `@(…)`; `@grouped(…)` | statement / `@objects` |
| `=` | section delimiter `= Name =`; exact-value correction `=200`; comparison `>=`, `<=` | multiple |
| `:` | terminates an object statement header | object statement |
| `,` | list separator (objects, tags, spec sub-args, groups, image options) | multiple |
| `*` | wildcard in object names/patterns; "all tags" in `@on *`; glob in image filenames | multiple |
| `&` | group reference prefix | object reference |
| `%` | warning-level prefix on a spec; percent unit in ranges; `%{…}` rule parameter | multiple |
| `\|` | rule invocation prefix | section / object body |
| `$` | `${…}` expression interpolation | anywhere in a line |
| `~` | approximate range operator | range |
| `"` | string literal delimiter; spec note delimiter | spec args |

Note `%` is **triply overloaded** (warning prefix, percent unit, rule parameter marker) and `#` is **quadruply overloaded**. Disambiguation is positional, so the lexer should be context-sensitive rather than a single flat token stream.

---

## 2. Indentation and block structure

Galen 2.0 is an **indentation-structured language** **[D — implicit in every example; explicitly "the syntax was changed completely" in 2.0]**. Nesting is expressed purely by leading whitespace; there are no braces or `end` markers.

Canonical nesting, from the docs:

```galen
= Header section =
    = Icons and text =
        header.icon:
            inside header 10px top left

        header.caption:
            text is "Greetings!"

    = User section =
        header.username:
            inside header 10px top right
```

Constructs that **open a block** (their children must be more-indented):

| Construct | Children are |
|---|---|
| `@objects` | object definition lines (which themselves nest for scoped locators) |
| `@groups` | group definition lines |
| `@set` (block form) | `name value` pairs |
| `@script` (block form) | raw JavaScript |
| `= Section =` | sub-sections, object statements, `@on`, `@if`, `@for`, `@forEach`, rule invocations |
| `@on <tags>` | object statements and nested statements |
| `@if` / `@elseif` / `@else` | any statement |
| `@for` / `@forEach` | any statement |
| `@rule <text>` | rule body statements |
| `objectName:` | spec lines and rule invocations |
| `\| rule invocation` | optional rule-body block (see §15.5) |

Constructs that **do not** open a block: `@import`, `@die`, `@ruleBody`, `@script <file>`, `@set <name> <value>` (inline form), and every spec line.

### 2.1 Exact algorithm (from `parser/IndentationStructureParser.java`) **[S]**

Galen is **line-oriented and two-phase**: it first builds a tree of *lines* (`StructNode`) using nothing but indentation, then parses each line's content independently. The linter mirrors this.

```
root = StructNode(indentation = -1)
stack = [root]
for each line:
    if line.trim() is empty      -> skip
    if line.trim() starts with # -> skip
    indent = 0
    for each leading char: indent += (char == '\t') ? 4 : 1     # TAB_SIZE = 4
    pop stack while stack.top.indentation >= indent
    parent = stack.top
    if parent already has children and firstChild.indentation != indent:
        throw SyntaxException("Inconsistent indentation")
    parent.addChild(node); stack.push(node)
```

Consequences that matter:

- **A tab is worth exactly 4 columns.** Mixed tabs and spaces are legal but will disagree with any editor whose tab width is not 4.
- **Blank and comment lines are invisible to structure.** They are filtered before the stack is touched, so they can never break a block or change nesting.
- **Siblings must match *exactly*.** It is not enough to be more-indented than the parent; the first child fixes the level and every later sibling must equal it. This is the single most common real error in `.gspec` files.
- The parser is restartable per line, which is why the IntelliJ lexer can also be line-local.

**Still open [?]:**

- Whether the indent *step* must be uniform across the file. It need not be — only siblings are compared — so a varying step is a style issue, not an error.
- `@elseif` / `@else` must appear at exactly the indentation of their matching `@if` **[I]**, which follows from the sibling rule.

---

## 3. Top-level grammar (EBNF)

This is a reconstruction sufficient to drive a recursive-descent parser. `INDENT` / `DEDENT` / `NEWLINE` are synthesised by the lexer as in Python.

```ebnf
SpecFile        = { TopStatement } ;

TopStatement    = ObjectsBlock
                | GroupsBlock
                | SetStatement
                | ScriptStatement
                | ImportStatement
                | RuleDefinition
                | Section
                | ObjectStatement          (* legal at top level — docs show bare
                                              "greeting-text:" after @script     *)
                | ConditionalStatement
                | LoopStatement
                | RuleInvocation
                | DieStatement
                | Comment ;

(* ---------- objects ---------- *)
ObjectsBlock    = "@objects" NEWLINE INDENT { ObjectDef } DEDENT ;
ObjectDef       = ObjectName [ Correction ] [ GroupedAnnotation ]
                  [ LocatorType ] LocatorValue NEWLINE
                  [ INDENT { ObjectDef } DEDENT ] ;   (* nested = scoped locators *)
LocatorType     = "id" | "css" | "xpath" ;
Correction      = "@(" CorrValue "," CorrValue "," CorrValue "," CorrValue ")" ;
CorrValue       = [ "+" | "-" | "=" ] Integer ;
GroupedAnnotation = "@grouped(" GroupName { "," GroupName } ")" ;

(* ---------- groups ---------- *)
GroupsBlock     = "@groups" NEWLINE INDENT { GroupDef } DEDENT ;
GroupDef        = ( GroupName | "(" GroupName { "," GroupName } ")" )
                  ObjectRef { "," ObjectRef } NEWLINE ;

(* ---------- sections & tags ---------- *)
Section         = "=" SectionName "=" NEWLINE INDENT { SectionBody } DEDENT ;
SectionBody     = Section | OnStatement | ObjectStatement | ConditionalStatement
                | LoopStatement | RuleInvocation | SetStatement | DieStatement ;
OnStatement     = "@on" TagList NEWLINE INDENT { SectionBody } DEDENT ;
TagList         = "*" | Tag { "," Tag } ;

(* ---------- object statements ---------- *)
ObjectStatement = ObjectRefList ":" NEWLINE INDENT { SpecLine | RuleInvocation } DEDENT ;
ObjectRefList   = ObjectRef { "," ObjectRef } ;
ObjectRef       = ObjectPattern | "&" GroupName | Interpolation ;

SpecLine        = [ "%" ] [ Note ] Spec NEWLINE ;
Note            = '"' { CHAR } '"' ;

(* ---------- control flow ---------- *)
ConditionalStatement =
      "@if" Interpolation NEWLINE INDENT { Statement } DEDENT
    { "@elseif" Interpolation NEWLINE INDENT { Statement } DEDENT }
    [ "@else" NEWLINE INDENT { Statement } DEDENT ] ;

LoopStatement   = ForLoop | ForEachLoop ;
ForLoop         = "@for" "[" Sequence "]" "as" Identifier NEWLINE
                  INDENT { Statement } DEDENT ;
Sequence        = SeqItem { "," SeqItem } ;
SeqItem         = Integer | Integer "-" Integer | Interpolation ;
ForEachLoop     = "@forEach" "[" ForEachSource "]" "as" Identifier
                  { "," ( "next" | "prev" | "index" ) "as" Identifier } NEWLINE
                  INDENT { Statement } DEDENT ;
ForEachSource   = ObjectPattern { "," ObjectPattern } | "&" GroupName ;

DieStatement    = "@die" String NEWLINE ;

(* ---------- variables / scripts / imports ---------- *)
SetStatement    = "@set" NEWLINE INDENT { Identifier Value NEWLINE } DEDENT
                | "@set" Identifier Value NEWLINE ;
ScriptStatement = "@script" FilePath NEWLINE
                | "@script" NEWLINE INDENT RawJavaScript DEDENT ;
ImportStatement = "@import" FilePath NEWLINE ;

(* ---------- rules ---------- *)
RuleDefinition  = "@rule" RuleText NEWLINE INDENT { Statement } DEDENT ;
RuleText        = { RuleTextChunk | RuleParam } ;
RuleParam       = "%{" Identifier [ ":" Regex ] "}" ;
RuleInvocation  = "|" RuleInvocationText NEWLINE
                  [ INDENT { Statement } DEDENT ] ;   (* rule body *)
RuleBodyMarker  = "@ruleBody" NEWLINE ;

(* ---------- interpolation ---------- *)
Interpolation   = "${" JavaScriptExpression "}" ;
```

> **Parser note.** `${…}` may appear inside virtually any token position — object names (`${item}:`), spec arguments (`inside header ${margin} top left`), loop bounds (`@for [1 - ${count("x-*")}]`), string literals (`text is "${data[i-1]}"`). The lexer should recognise `${` … matching `}` as an **opaque atomic token** and let a later pass optionally parse the JS. Brace matching must handle nested braces and braces inside JS string literals **[?]** — verify how the reference implementation scans; naive first-`}` matching breaks on `${ {a:1}.a }`.

---

## 4. Object definitions — `@objects`

### 4.1 Basic form

```galen
@objects
    search_panel            id      search-bar
    search_panel_input      xpath   //div[@id='search-bar']/input[@type='text']
    search_panel_button     css     #search-bar a
```

Locator types **[D]**:

| Type | Meaning |
|---|---|
| `id` | searches for object by id in DOM |
| `css` | CSS selectors |
| `xpath` | XPath expressions |

### 4.2 Implicit `css`

The locator type may be omitted; the locator is then treated as CSS **[D]**:

```galen
@objects
    search_panel            #search-bar
    search_panel_input      #search-bar input[type='text']
    search_panel_button     #search-bar a
```

**Parsing hazard [I]:** distinguishing `name css #foo` from `name #foo` requires checking whether the second field is exactly `id`, `css`, or `xpath`. A CSS locator that *is* literally the word `css` (e.g. a class-less element selector) would be misparsed — worth a lint warning (`ambiguous-locator-type`). Also note the locator value may itself contain spaces (`#search-bar a`), so the locator is "everything from field 2 (or 3) to end of line" **[I]**.

### 4.3 Nested (scoped) objects

```galen
@objects
    search_panel   #search-bar
        input      input[type='text']
        button     a
```

Galen wraps child locators in the parent's scope and creates references named with a **dot**: `search_panel`, `search_panel.input`, `search_panel.button` **[D]**.

Consequences for the linter:

- Object names in references may contain `.` — the dot is a *scope separator*, not part of a spec-argument delimiter.
- Nesting depth is unbounded **[I]**; `a.b.c.d` is legal.
- **[?]** Whether a nested child may itself declare an explicit locator type (`input xpath ./input`) is not shown but should be assumed legal.

### 4.4 Multiple objects (`*` in the name)

```galen
@objects
    menu_item-*     css     #menu li a
```

Galen finds all matching elements and names them `menu_item-1`, `menu_item-2`, … — the asterisk is replaced by the element's **1-based index** **[D]**.

### 4.5 Object corrections

Syntax: `@(left, top, width, height)` placed between the object name and the locator **[D]**.

```galen
@objects
    some_test_object    @(0, 0, -50, 0)     id  some-container   # width  −50px
    some_test_object    @(-30, +100, 0, 0)  id  some-container   # move left 30, down 100
    some_test_object    @(0, 0, 0, +200)    id  some-container   # height +200px
    some_test_object    @(0, 0, 0, =200)    id  some-container   # height set to exactly 200
```

Each of the four values is:

| Form | Meaning |
|---|---|
| `0` | no correction |
| `+N` / `N` | increase by N **[I]** (docs show `+100`; bare `-50` is used for decrease, so bare numbers are signed offsets) |
| `-N` | decrease by N |
| `=N` | set to exactly N |

Order is **left, top, width, height** **[I — deduced from the three examples: slot 3 changes width, slot 4 changes height, slot 1 moves horizontally, slot 2 moves vertically]**. Units are pixels; no unit suffix appears **[D]**.

Lint checks: exactly four values; integer-only; `=` with a negative value is nonsensical; a correction of `@(0,0,0,0)` is dead code.

### 4.6 Inline group annotation

```galen
@objects
    header          #header
        logo @grouped(image_validation)  img.logo
```

`@grouped(...)` assigns the object to one or more groups without a `@groups` block **[D]**. **[?]** Whether multiple group names are comma-separated inside the parentheses is not shown, but is the natural reading given `@groups` syntax.

**Ordering [?]:** the example places `@grouped(...)` where a correction would go. Whether `@(…)` and `@grouped(…)` may both appear, and in which order, is undocumented.

---

## 5. Object groups — `@groups`

Available **since 2.2** **[D]**.

```galen
@objects
    header          #header
    menu            ul.menu
    content         #content
    footer          #footer

@groups
    skeleton_elements   header, menu, content, footer
```

Reference a group with `&`:

```galen
= Skeleton =
    &skeleton_elements:
        inside screen 0px left right
```

which is exactly equivalent to:

```galen
= Skeleton =
    header, menu, content, footer:
        inside screen 0px left right
```

Multiple groups on one line use parentheses **[D]**:

```galen
@groups
    (skeleton_elements, mainframe)  header, menu, content, footer
    mainframe                       navigation_bar
```

Groups accumulate across lines — `mainframe` ends up with the four skeleton elements **plus** `navigation_bar` **[D]**.

Groups work in `@forEach` **[D]**:

```galen
= Mainframe =
    @forEach [&mainframe] as item
        ${item}:
            inside screen 10px left
```

Lint checks: `&unknownGroup` reference; group listing an undefined object; empty group; group name colliding with an object name **[?]** (unclear whether that is an error in Galen — treat as a warning).

---

## 6. Object references and pattern matching

Galen's object-matching mini-language **[D]**:

| Symbol | Matches |
|---|---|
| `*` | any sequence of symbols |
| `#` | digits only |

```galen
menu_item-1, menu_item-2, menu_item-3:
    width 100 to 150px
    height 50px

menu-*:                 # any suffix
    width 100 to 150px
    height 50px

menu_item-#:            # numeric suffix only
    width 100px
```

Patterns are used in: object statement headers, `contains`, `count`, `@forEach [...]`, `image … ignore-objects [...]`, and the JS functions `count()` / `findAll()` **[D]**.

Object **name** character set **[?]**: examples use letters, digits, `_`, `-`, `.` (scope), `*`, `#`. Assume `[A-Za-z0-9_.\-]` plus wildcards; confirm against the parser before rejecting anything.

**Important lint nuance:** a pattern that matches nothing at runtime is not a syntax error, but a pattern that can *never* match any declared object (e.g. `menu_itme-*` typo) is a high-value static finding — see rule `GL201` in §20.

---

## 7. Special objects

Usable without declaration **[D]**:

| Name | Meaning | Notes |
|---|---|---|
| `screen` | the whole page area inside the browser, including the part not visible | has `width`/`height` for relative ranges |
| `viewport` | the currently visible area | useful for fixed elements |
| `parent` | the component element itself | **only inside a component spec** |
| `self` | identical to `parent` | **only inside a component spec** |
| `global` | pseudo-object for validations not tied to a real object (e.g. `count`) | |

```galen
= Main =
    feedback_button:
        inside viewport 0px right
        centered vertically inside viewport

= Component =
    icon:
        inside parent 0px top left

    self:
        image imgs/component.png

    global:
        count any menu_item-* is 4
```

Lint checks:

- `parent` / `self` used in a file that is not (statically knowable to be) a component → warning, not error (a `.gspec` can be used as both a page spec and a component).
- Redeclaring a special object in `@objects` → warning (`shadows-special-object`).
- `global:` used with a spec other than `count` → **[?]** probably invalid; warn.

---

## 8. Sections, tags — `= … =` and `@on`

### 8.1 Sections

Declared with `=` at the start **and** end of the line **[D]**. Sections nest arbitrarily.

```galen
= Header section =
    = Icons and text =
        header.icon:
            inside header 10px top left
```

Section names are free text **[I]** — they are report headings, not identifiers. Duplicate names are legal.

### 8.2 Tags — `@on`

```galen
= Main section =
    @on mobile
        menu:
            height 300 px

    @on desktop
        menu:
            height 40 px
```

- `@on *` applies to **all** tags **[D]**.
- Comma-separated tag lists: `@on mobile, desktop` **[D]**.
- Tags are matched against the tags supplied by the test suite / JS test at run time.

Lint checks: unknown-tag detection requires configuration (a project-level list of valid tags) — worth supporting via a config file. Also: nested `@on` inside `@on` **[?]** — undocumented; probably legal (intersection), flag as a warning until verified.

---

## 9. Object statements and spec lines

An object statement is `<objectRefList>:` followed by an indented block of spec lines and/or rule invocations.

```galen
menu_item-1, menu_item-2:
    width 100 to 150px
    height 50px
```

A **spec line** has the shape:

```
[ "%" ] [ '"' note '"' ] <spec-name> <spec-args…>
```

### 9.1 Warning-level prefix `%`

Since **1.2** **[D]**. A spec prefixed with `%` reports as a *warning* on failure and does not fail the suite; shown yellow in the HTML report.

```galen
login-button:
    text is "Login"
    % width 100px
```

### 9.2 Notes

Since **1.6** **[D]**. A double-quoted string placed **before** the spec becomes a report sub-section label.

```galen
header-logo:
    inside header 5 to 15px top, 0 to 10px left
    near header-text 5 to 30px left
    "should be squared" width 100% of header-logo/height
```

**[?]** Combination order when both are present — presumably `% "note" spec`. Verify.

### 9.3 Global visibility precondition

> **IMPORTANT!** For each spec Galen always checks that all included elements in a spec are visible on page. Galen tries to act as a real user and if a user doesn't see an object on page then it is not there. **[D]**

This matters for a *semantic* lint rule: combining `absent` with any positional spec on the same object is contradictory (`GL303`).

---

## 10. Ranges — the universal value grammar

The **range** is the shared value type for `width`, `height`, `near`, `inside`, `on`, `above`, `below`, `left-of`, `right-of`, `count`, `color-scheme`, and alignment error rates **[D]**.

### 10.1 Absolute ranges (px)

```galen
width 100px          # exact
width 50 to 200 px   # between
width > 40 px        # greater than
width < 40 px        # less than
width >= 40 px       # greater than or equals
width <= 40 px       # less than or equals
width ~ 100 px       # approximate
```

### 10.2 Relative ranges (%)

```galen
width 50 % of screen/width
width ~ 95 % of screen/width
height > 40 % of screen/height
width 30 to 100 % of screen/width
width 100 % of main/width
height 90 to 100 % of main/height
width 100% of viewport/width
```

Form: `<rangeExpr> % of <objectName>/<property>` where `<property>` ∈ `{ width, height }` **[D]**.

### 10.3 Range EBNF

```ebnf
Range         = AbsoluteRange | RelativeRange | ExprRange ;

AbsoluteRange = RangeValue Unit ;
RelativeRange = RangeValue "%" "of" ObjectName "/" ( "width" | "height" ) ;

RangeValue    = Number
              | Number "to" Number
              | ( ">" | "<" | ">=" | "<=" | "~" ) Number ;

Unit          = "px" | "%" ;
Number        = [ "-" ] Digit { Digit } [ "." { Digit } ] ;   (* [?] decimals *)
ExprRange     = Interpolation [ Unit ] ;   (* e.g. ${commonHeaderMargin}, ${size} px *)
```

Notes:

- Whitespace between number and unit is optional: `100px`, `100 px`, `50 to 200 px`, `100% of …` all appear **[D]**.
- **Negative values are legal** in positional specs: `inside partly container -10px top left` **[D]** (discouraged by the docs).
- Decimal values: **[?]** never shown. Assume integers; do not hard-error on decimals.
- `count` uses ranges **without a unit**: `is 3`, `is 4 to 5`, `is < 6` **[D]**.
- A whole range may come from a variable: `inside header ${commonHeaderMargin} top left`, where `commonHeaderMargin` was `@set` to `10 to 20px` **[D]** — so `${…}` can supply *the entire range including the unit*. The linter must not demand a literal unit when the value is interpolated.

### 10.4 Sides

Positional specs take side keywords after the range **[D]**:

`left`, `right`, `top`, `bottom`

Multiple sides may follow one range (`10px left right`), and multiple `range+sides` groups are comma-separated (`10px left right, 20px top bottom`).

---

## 11. Spec reference — complete argument grammars

Complete list of spec names **[D]** (the docs' bullet list omits several that are documented in their own sections — the full set is below):

`near`, `above`, `below`, `left-of`, `right-of`, `inside`, `width`, `height`, `aligned`, `text`, `css`, `centered`, `absent`, `visible`, `contains`, `on`, `component`, `count`, `ocr`, `color-scheme`, `image`

### 11.1 `near`

```ebnf
NearSpec = "near" ObjectRef SideGroup { "," SideGroup } ;
SideGroup = Range Side { Side } ;
```

```galen
textfield:
    near button 10px left
    near button 5 to 15px left
    near button 5px top
    near button 5px bottom left
    near button 5px top, 10px left
```

### 11.2 `above` / `below`

```ebnf
AboveSpec = "above" ObjectRef Range ;
BelowSpec = "below" ObjectRef Range ;
```

```galen
caption:
    above description 10 to 20 px

description:
    below caption 10 to 20 px
```

Sugar over `near` for readability **[D]**. No side keywords.

### 11.3 `left-of` / `right-of`

Added in **1.6** **[D]**.

```ebnf
LeftOfSpec  = "left-of"  ObjectRef Range ;
RightOfSpec = "right-of" ObjectRef Range ;
```

```galen
textfield:
    left-of button 10px

button:
    right-of textfield 10px
```

### 11.4 `inside`

```ebnf
InsideSpec = "inside" [ "partly" ] ObjectRef [ SideGroup { "," SideGroup } ] ;
```

```galen
button:
    inside container 10 px top left
    inside container 10px left right, 20px top bottom
    inside partly container 10px top left
    inside partly container -10px top left    # legal but discouraged
    inside container                          # no sides — legal, discouraged
```

- `inside` — element must be **completely** inside.
- `inside partly` — only the listed ranges are checked; the element need not be fully contained **[D]**.
- Omitting sides is legal but "not a good practice" — a natural style lint (`GL401`).

### 11.5 `width` / `height`

```ebnf
WidthSpec  = "width"  Range ;
HeightSpec = "height" Range ;
```

```galen
button:
    width 100 px
    height 25px
    width < 101 px

comments:
    width 100 % of main/width
    width 95 to 100 % of main/width
```

### 11.6 `aligned`

```ebnf
AlignedSpec = "aligned" Direction Edge ObjectRef [ ErrorRate ] ;
Direction   = "horizontally" | "vertically" ;
Edge        = "all" | "top" | "bottom" | "left" | "right" | "centered" ;
ErrorRate   = Number "px" ;
```

**Valid Direction × Edge combinations [D]:**

| | `all` | `top` | `bottom` | `left` | `right` | `centered` |
|---|:-:|:-:|:-:|:-:|:-:|:-:|
| `horizontally` | ✔ (top+bottom edges) | ✔ | ✔ | ✘ | ✘ | ✔ |
| `vertically` | ✔ (left+right edges) | ✘ | ✘ | ✔ | ✔ | ✔ |

This table is directly lintable (`GL302` — invalid direction/edge pairing).

```galen
menu_item-1:
    aligned horizontally all menu_item-2
    aligned horizontally top menu_item-2
    aligned vertically left menu_item-2
    aligned horizontally all menu_item-2 1px    # 1px error rate
```

### 11.7 `text`

```ebnf
TextSpec  = "text" [ TextOp ] Matcher String ;
TextOp    = "lowercase" | "uppercase" | "singleline" ;
Matcher   = "is" | "contains" | "starts" | "ends" | "matches" ;
```

| Matcher | Meaning |
|---|---|
| `is` | exact match |
| `contains` | substring |
| `starts` | prefix |
| `ends` | suffix |
| `matches` | **Java** regular expression |

| Text op | Effect |
|---|---|
| `lowercase` | lower-case all letters before comparing |
| `uppercase` | upper-case all letters before comparing |
| `singleline` | replace all newline symbols with a space |

```galen
greeting:
    text is "Welcome john@example.com to our cool website!"
    text starts "Welcome"
    text ends "website!"
    text contains "to our cool"
    text matches "Welcome .* to our cool website!"
    text lowercase is "welcome john@example.com to our cool website!"
    text uppercase starts "WELCOME"
    text singleline is "welcome john@example.com to our cool website!"
```

Galen sees the text as a browser renders it (via Selenium) — **white space is collapsed**, unlike raw HTML **[D]**. **[?]** Whether multiple text ops can be chained (`text lowercase singleline is …`) is not documented.

Lintable: with `matches`, the argument should be validated as a **Java** regex (`GL305`) — note Java-specific constructs differ from JS/PCRE.

### 11.8 `css`

```ebnf
CssSpec = "css" CssPropertyName Matcher String ;
```

```galen
login-button:
    css font-size is "18px"
    css font-family starts "Helvetica"
    css font-family ends "sans-serif"
    css font-family contains "Arial"
    css font-family matches ".*Arial.*"
```

- Same matchers as `text` **[D]**.
- **`lowercase` and `uppercase` text operations are NOT available in `css`** **[D]** → lintable (`GL306`). `singleline` is not mentioned either way **[?]**.
- The docs explicitly discourage `css`: *"Use this `css` spec rarely and wisely."* → opt-in style rule (`GL402`).

### 11.9 `centered`

```ebnf
CenteredSpec = "centered" Direction2 Relation ObjectRef [ ErrorRate ] ;
Direction2   = "horizontally" | "vertically" | "all" ;
Relation     = "inside" | "on" ;
```

```galen
button:
    centered horizontally inside box
    centered all inside box

label:
    centered horizontally on box
    centered horizontally inside box 10px      # 10px error rate
```

`inside` requires containment; `on` allows the element to stick out of the reference element's edges **[D]**.

### 11.10 `absent`

No arguments **[D]**. True when the element is missing from the DOM **or** present but not visible (e.g. `display:none`).

```galen
comments:
    absent
```

### 11.11 `visible`

No arguments **[D]**. The inverse of `absent`.

```galen
comments:
    visible
```

### 11.12 `contains`

```ebnf
ContainsSpec = "contains" [ "partly" ] ObjectPattern { "," ObjectPattern } ;
```

```galen
comments:
    contains comment-header, comment-send-button, comment-textfield

menu:
    contains menu_item-*

box:
    contains partly box-item-1, box-item-2
```

### 11.13 `on`

```ebnf
OnSpec  = "on" Corner "edge" ObjectRef SideGroup { "," SideGroup } ;
Corner  = ( "top" | "bottom" ) ( "left" | "right" ) ;
```

```galen
user-picture-label:
    on top left edge user-picture 20 px left, 10px bottom
    on bottom left edge user-picture 20px right, 10 px top
```

Semantics: the element is offset from the named **corner** of the other element **[D]**. **[?]** Whether a single-word corner (`on left edge …`) is legal is not shown.

> Note the collision: `on` is both a **spec** (inside an object body) and part of `@on` (a tag statement). They are distinguished by the `@` and by context.

### 11.14 `component`

```ebnf
ComponentSpec = "component" [ "frame" ] SpecFilePath { "," ArgName ArgValue } ;
```

```galen
= All user profiles =
    user-profile-*:
        component user-profile.gspec

= Main section =
    banner-frame:
        component frame banner.gspec        # iframe support

    header:
        component header-component.gspec, isUserLogged true, userName "John Johnson"
```

Inside the component file, arguments are read via `${…}` **[D]**:

```galen
@objects
    user_name       .user-name

= Header =
    @if ${isUserLogged}
        user_name:
            text is "${userName}"
```

Within a component spec, `parent` / `self` refer to the component's host element **[D]**.

Lintable: the referenced `.gspec` file exists (`GL501`); argument list is well-formed `name value` pairs; unquoted vs. quoted values (`true` vs `"John Johnson"`) **[I]** — quotes needed when the value contains spaces.

### 11.15 `count`

Added in **2.1** **[D]**.

```ebnf
CountSpec = "count" Filter ObjectPattern "is" RangeValue ;
Filter    = "any" | "visible" | "absent" ;
```

```galen
= Main =
    global:
        count any menu_item-* is 3
        count any menu_item-* is 4 to 5
        count any menu_item-* is < 6
        count visible menu_item-* is 4 to 5
        count absent  menu_item-* is 4 to 5
```

Range takes **no unit** **[D]**. Normally used on `global:` **[D]**.

### 11.16 `ocr`

Added in **2.4** **[D]**. Requires a Google Vision key via the `galen.ocr.google.vision.key` property.

```ebnf
OcrSpec = "ocr" "text" [ TextOp ] Matcher String ;
```

```galen
= Checking text =
    header.caption:
        ocr text is "My Awesome Website!"
```

Same five matchers as `text` **[D]**.

### 11.17 `color-scheme`

```ebnf
ColorSchemeSpec = "color-scheme" ColorEntry { "," ColorEntry } ;
ColorEntry      = RangeValue "%" Color ;
Color           = ColorName | HexColor | Gradient ;
Gradient        = ColorAtom "-" ColorAtom { "-" ColorAtom } ;
HexColor        = "#" HexDigit{3} | "#" HexDigit{6} ;
```

```galen
login-form:
    color-scheme 10% white, 4 to 5 % black, < 30% #f845b7

login-form:
    color-scheme ~80% white-gray, ~20% #000-#555-#955      # gradients, since 2.3
```

Note both named colours (`white`, `black`, `gray`) and hex (3- and 6-digit) are used **[D]**. The valid colour-name set is **[?]** — presumably Java AWT / CSS names; do not hard-error on unknown names.

### 11.18 `image`

The most option-rich spec.

```ebnf
ImageSpec   = "image" ImageOption { "," ImageOption } ;
ImageOption = "file" FilePathOrGlob
            | "error" Number ( "px" | "%" )
            | "tolerance" Number
            | "stretch"
            | "area" Number Number Number Number          (* left top width height *)
            | "analyze-offset" Integer
            | "crop-if-outside"
            | "ignore-objects" ( ObjectPattern | "[" ObjectPattern { "," ObjectPattern } "]" )
            | "filter"    FilterSpec        (* both images *)
            | "filter-a"  FilterSpec        (* original image only *)
            | "filter-b"  FilterSpec        (* sample image only *)
            | "map-filter" FilterSpec ;     (* comparison map *)
```

```galen
menu_item-1:
    image file imgs/menu_item-1.png, error 12px
    image file imgs/menu_item-1.png, error 4%
    image file imgs/menu_item-1.png, error 4%, tolerance 80
    image file imgs/menu_item-1.png, error 4%, stretch
    image file imgs/menu_item-1.png, error 4%, area 10 10 100 30
    image file imgs/menu_item-1.png, analyze-offset 2
    image file imgs/menu_item-1.png, crop-if-outside

content:
    image file imgs/content.png, ignore-objects banner
    image file imgs/content.png, ignore-objects [banner-*, ad]

header-text:
    image file image-1.png, file image-2.png, file image-3.png, error 20px
    image file image-*.png, error 20px

menu_item-1:
    image file item-1.png, error 1%, filter blur 4, filter saturation 0, map-filter denoise 5

login-button:
    image file imgs/login-button.png, filter-a blur 10, error 4%
    image file imgs/login-button.png, filter-b contrast 200, error 4%
```

**Option semantics [D]:**

| Option | Meaning |
|---|---|
| `file <path>` | sample image. Repeatable — Galen picks the sample with the fewest mismatching pixels. Supports `*` globs. |
| `error <N>px` / `<N>%` | max allowed mismatching pixels, absolute or relative |
| `tolerance <N>` | max allowed colour difference between two compared pixels. **Default 30.** Higher ⇒ fewer mismatches but weaker comparison. |
| `stretch` | scale the sample to the element size instead of padding with black |
| `area <l> <t> <w> <h>` | use only this region of the sample image |
| `analyze-offset <N>` | search for the best-fitting offset up to N px before comparing |
| `crop-if-outside` | crop out-of-border areas (rounding errors from `rem`/`em` layouts). Without it, Galen reports "area is outside the original image". |
| `ignore-objects <pattern\|[patterns]>` | exclude the regions of other objects from the comparison |

**Image filters [D]:**

| Filter | Argument | Notes |
|---|---|---|
| `blur` | `<radius>` | |
| `saturation` | `<level>` | 0 = greyscale, 100 = unchanged, 50 = half-coloured |
| `contrast` | `<level>` | valid range **0 to 258** — directly lintable |
| `denoise` | `<radius>` | **only valid as `map-filter`** (works on black/white images) — lintable |
| `quantinize` | `<colorsAmount>` | reduce colour count (note the docs' spelling — *quantinize*, not *quantize*) |
| `mask` | `<maskImagePath>` | applies a mask to the alpha channel; black = transparent, white = opaque |
| `replace-colors` | `<colors…> with <finalColor> tolerance <N> radius <N>` | e.g. `filter-a replace-colors #111 #555-#777 with #fff tolerance 10 radius 2` |

Filter application prefixes: `filter` (both images), `filter-a` (original only), `filter-b` (sample only), `map-filter` (comparison map).

Report colour key **[D]** (useful for docs, not linting): red = mismatching pixels far beyond tolerance; yellow = 30–80 colour difference from tolerance; green = closest to tolerance.

---

## 12. Control flow: `@if`, `@for`, `@forEach`, `@die`

### 12.1 `@if` / `@elseif` / `@else`

```galen
= Banners =
    @if ${isVisible("banner-1")}
        banner-1:
            width 300 px
            height 100 px
    @elseif ${isVisible("banner-2")}
        banner-2:
            width 300 px
            height 100 px
    @else
        banner-3:
            width 300 px
            height 100 px
```

The condition is a JavaScript expression in `${…}` **[D]**. `@elseif`/`@else` are optional and repeatable (`@elseif`) **[D]**.

Lint: `@elseif`/`@else` without a preceding `@if` at the same indent (`GL103`); empty branch body (`GL104`).

### 12.2 `@for`

```galen
= Main section =
    @for [1 - 9] as index
        menu_item-${index}:
            left-of menu_item-${index + 1} 10px
```

Complex sequences mix single values and ranges **[D]**:

```galen
    @for [1 - 5, 7, 9, 14, 20 - 25] as index
```

The bound may itself be an expression **[D]**:

```galen
@for [ 1 - ${count("menu_item-*")} ] as objectName
```

and the whole sequence can come from a JS function **[D]**:

```galen
@script allEven.js

@for  [${allEven("menu_item-*")}] as index
    menu_item-${index}:
        height 100px
```

Note the loop variable is usable inside JS expressions (`${index + 1}`) **[D]**.

Lint: descending range (`[9 - 1]`) **[?]**; overlapping sequence items; unused loop variable (`GL204`).

### 12.3 `@forEach`

Iterates over **objects matching a pattern** rather than numbers **[D]**.

```galen
= Main section =
    @forEach [menu_item-*] as itemName
        ${itemName}:
            height 30px
```

Extra bindings — `next`, `prev`, `index` **[D]**:

```galen
    @forEach [menu_item-*] as itemName, next as nextItem
        ${itemName}:
            left-of ${nextItem} 10px

    @forEach [menu_item-*] as itemName, prev as previousItem
        ${itemName}:
            right-of ${previousItem} 10px
```

| Binding | Behaviour |
|---|---|
| `next as X` | iteration stops before the last element (there is no next) |
| `prev as X` | iteration starts from the second element |
| `index as X` | 1-based index |

```galen
@objects
    menu_item-*        #menu ul li

@script
    data = ["Home", "My Notes", "About", "Contact"];

= Menu =
    @forEach [menu_item-*] as item, index as i
        ${item}:
            text is "${data[i-1]}"
```

Group sources are supported: `@forEach [&mainframe] as item` **[D]**.

**[?]** Whether multiple extra bindings can be combined (`, next as n, index as i`) is not shown but is the natural reading.

### 12.4 `@die`

Since **2.3** **[D]**.

```galen
@if ${count("menu.item-*") === 0}
    @die "There are no menu items"
```

Takes a single string message; terminates the spec with an error.

---

## 13. Variables, scripts, expressions — `@set`, `@script`, `${…}`

### 13.1 `@set` (block form)

```galen
@set
    commonHeaderMargin    10 to 20px
    contentMargin  ~ 20px

= Header =
    header_icon:
        inside header ${commonHeaderMargin} top left

= Content =
    article-description:
        inside main ${contentMargin} left right
```

The value is **raw text substituted into the spec**, not a typed value — it can carry an operator and a unit (`~ 20px`) **[D]**.

**Variable names** validate as `[a-zA-Z_][a-zA-Z0-9_]*` (`PageSpecHandler.isValidVariableName`) **[S]** — note this is *narrower* than object names, which also allow `-` and `.`. A `@set` entry named `content-margin` is therefore invalid even though an object of that name is fine.

### 13.2 `@set` (inline form)

```galen
@set menuMargin  ${find("menu_item-2").left() - find("menu_item-1").right()}
```

**[D]** — shown in the JS API section.

### 13.3 `@script <file>`

Loads a JavaScript file whose functions then become callable in `${…}` **[D]**:

```javascript
// i18n.function.js
this.i18n = function (name) {
    // define a code for handling i18n
};
```

```galen
@script i18n.function.js

greeting-text:
    text is "${i18n('header.greeting.text')}"
```

Also used to load JS-based custom rules (§15.4).

### 13.4 `@script` (block form)

```galen
@script
    data = ["Home", "My Notes", "About", "Contact"];
```

Everything indented under `@script` is raw JavaScript **[D]** — the linter's Galen lexer must **suspend Galen tokenisation** for this block and hand the text to a JS parser (or skip it).

### 13.5 `${…}` expressions

JavaScript, evaluated in Galen's context. Appears in:

- range values — `width ${size} px`, `inside header ${commonHeaderMargin} top left`
- object names — `${item}:`, `menu_item-${index}:`
- inside string literals — `text is "${data[i-1]}"`, `text is "${userName}"`
- loop bounds — `@for [1 - ${count("menu_item-*")}] as index`
- conditions — `@if ${isVisible("banner-1")}`
- rule bodies — `width 100% of ${name}/height`

---

## 14. Imports — `@import`

```galen
@import header.gspec
@import footer.gspec

# and now goes the spec for your home page
```

Galen **merges** all objects and specs from the imported files into the importing file; imported objects are then referenceable **[D]**.

### Resolution semantics (from `speclang2/pagespec/ImportProcessor.java`) **[S]**

- Paths resolve against a **context path** — the parent directory of the file currently being processed (`GalenUtils.getParentForFile`). Imports therefore nest relative to *each* file, not to the entry-point spec.
- Lookup goes through `GalenUtils.findFileOrResourceAsStream`, so a path may resolve to a **classpath resource** as well as a file on disk. A path that does not exist on disk is therefore **not** proof of a broken import.
- **Repeated and circular imports are already handled.** Each file gets an id via `GalenUtils.calculateFileId`; ids are recorded in `processedImports` and an already-seen file is silently skipped. A cycle does not hang or fail — the second visit is simply dropped.

Linter implications — this is the single most important cross-file feature:

- Object resolution must be **transitive** across imports, so the linter still needs a module graph.
- A cycle is *not* a runtime error, so `GL502` should be a warning about confusing structure, not an error.
- A duplicate `@import` is silently ignored, making the second one dead code — `GL505`, warning.
- `GL501` (missing file) must stay a warning, because classpath resources are invisible to a filesystem check.
- Object name collisions between importer and imported file — **[?]** last-wins vs. error. Warn.

## 14.1 `@lib` — undocumented **[S]**

`@lib` appears nowhere in the official guide but is a real statement, dispatched by `MacroProcessor` to `LibProcessor` (which extends `ImportProcessor`).

```galen
@lib <libraryName>
```

- Takes a single inline argument; no block body.
- Loads a spec library **bundled inside the Galen jar**, from `/spec-libs/<name>/<name>.gspec`.
- The name must appear in the whitelist at `/spec-libs/libs.list`; otherwise Galen raises `SyntaxException("Cannot find library: <name>")`.

So `@lib` is `@import` restricted to blessed, embedded libraries. A linter cannot validate the name without reading `libs.list` out of the Galen distribution, so treat an unknown name as a warning at most.

---

## 15. Custom rules — `@rule`, `|`, `@ruleBody`

Available since **1.6** **[D]**; rule bodies since **2.2** **[D]**.

### 15.1 Parameterised rules (section context)

```galen
@rule %{name} should be squared
    ${name}:
        width 100% of ${name}/height

= Main section =
    | header-icon should be squared
    | footer-icon should be squared
```

`%{param}` in the rule *text* defines a capture; invoking with `|` matches the invocation text against the rule text and binds the captures, which are then readable as `${param}` in the body **[D]**.

### 15.2 Object-context rules

```galen
@rule should be squared
    width 100% of ${objectName}/height

= Main =
    header-icon:
        | should be squared

    footer-icon:
        | should be squared
```

When invoked inside an object statement, Galen supplies an implicit **`objectName`** parameter **[D]**.

### 15.3 Custom parameter regexes

Default capture regex is `.*` **[D]**. Override with `%{name: regex}`:

```galen
@rule %{object} should be squared with %{size: [0-9]+} pixel size
    ${object}:
        width ${size} px
        height ${size} px

= Main =
    | logo should be squared with 100 pixel size
```

> **Matching hazard the linter should model:** with the default `.*` regex, rule texts are greedy and can be ambiguous. Two rules whose texts can both match one invocation is a real defect worth reporting (`GL601`), as is an invocation that matches **no** rule (`GL602`).

### 15.4 JavaScript-based rules

Defined in a `.js` file loaded with `@script` **[D]**. The callback receives `(objectName, parameters)`:

- **`objectName`** — the object the rule was applied to, or `null` for a section-level invocation.
- **`parameters`** — an object with a field per `%{…}` capture. For rule text `located near %{name} with %{distance} pixel margin`, you get `parameters.name` and `parameters.distance`.

Inside the callback:

| Function | Use |
|---|---|
| `addObjectSpecs(objectName, specs)` | add specs to a named object (use for section-level rules); `specs` is an array of strings |
| `addSpecs(specs)` | add specs to the object the rule was applied to (object-level rules only) |
| `doRuleBody()` | invoke the rule's body block |

```javascript
rule("%{objectPattern} are equally distant from each other", function (objectName, parameters) {
    var allObjects = findAll(parameters.objectPattern);

    if (allObjects.length > 1) {
        var distance = allObjects[1].left() - allObjects[0].right();
        for (var i = 0; i < allObjects.length - 1; i++) {
            var nextObject = allObjects[i + 1];

            this.addObjectSpecs(allObjects[i].name, [
                "near " + allObjects[i + 1].name + " " + distance + " px left"
            ]);
        }
    } else {
        throw new Error("Not enough objects for pattern: " + parameters.objectPattern);
    }
});
```

```galen
@script my-rules.js

@objects
    menu_item-*        #menu li a

= Menu =
    | menu_item-* are equally distant from each other
```

**Linter consequence:** rules can be defined in JS, so "unknown rule" cannot be a hard error unless the linter also parses every `@script`-loaded file for `rule("…", …)` calls. Recommend: parse JS files for top-level `rule(` string literals and merge them into the rule table; otherwise downgrade `GL602` to a warning when any `@script` is present.

### 15.5 Rule bodies

```galen
@rule if %{objectName} is visible
    @if ${isVisible(objectName)}
        @ruleBody
```

Used as a block-opening invocation:

```galen
= Main section =
    | if banner is visible
        banner
            width 1000px
```

(Note the docs' example is missing the `:` after `banner` — see §19.)

JS equivalent:

```javascript
rule("if %{objectName} is visible", function (scopeObject, parameters) {
    if (isVisible(parameters.objectName)) {
        this.doRuleBody();
    }
});
```

Lint: `@ruleBody` outside a `@rule` definition (`GL603`); a rule invocation with a body block whose rule never calls `@ruleBody`/`doRuleBody()` (`GL604`).

---

## 16. Galen Specs JS API surface

Available inside `${…}` blocks **[D]**:

| Function | Returns |
|---|---|
| `count(objectPattern)` | number of objects matching the pattern |
| `find(objectName)` | a page element (see below) |
| `findAll(objectPattern)` | array of page elements |
| `isVisible(objectName)` | `true` if visible on page; `false` if hidden or absent |
| `isPresent(objectName)` | `true` if present in the DOM; `false` if absent |

Page-element members **[D]**:

| Member | Returns |
|---|---|
| `.left()` | left edge, from the screen's left edge |
| `.right()` | right edge, from the screen's left edge |
| `.top()` | top edge, from the screen's top edge |
| `.bottom()` | bottom edge, from the screen's top edge |
| `.width()` | width |
| `.height()` | height |
| `.isVisible()` | visible on page |
| `.isPresent()` | present in the DOM |
| `.name` | element name (property, not a function) |

Global element objects **[D]**: `viewport`, `screen` — both page elements, so `${viewport.width() - 100}`, `${screen.width()}`.

Component arguments are injected into this scope as plain variables (`${isUserLogged}`, `${userName}`) **[D]**, as are `@set` variables, loop variables, and rule parameters.

**Note the `.name` trap:** `.name` is a property while everything else is a method. `${find("x").name()}` is a runtime error — a nice lint rule if the linter parses JS (`GL701`).

---

## 17. Name resolution & scoping model

To do useful semantic linting, build these tables per spec file (transitively across `@import`):

1. **Object table** — name → { locatorType, locator, corrections, groups, declaringFile, line, isPattern }.
   - Nested definitions contribute dotted names.
   - `*` in a declared name makes it an *object family*: a reference `menu_item-3` resolves against the family `menu_item-*`.
2. **Group table** — group → set of object references (accumulating across `@groups` lines and `@grouped(...)` annotations).
3. **Variable table** — from `@set` (block + inline), `@for`/`@forEach` bindings, rule `%{…}` parameters, and component arguments. Scoped to the enclosing block.
4. **Rule table** — rule text pattern (compiled from `%{name[: regex]}`) → definition site. Merged from `@rule` and from `rule("…")` calls in `@script` files.
5. **Tag set** — every tag mentioned in `@on`.
6. **Import graph** — for cycle detection and cross-file resolution.

**Reference resolution order for an object name** (proposal, since undocumented **[?]**):

1. Special objects (`screen`, `viewport`, `parent`, `self`, `global`).
2. Loop / `@set` / rule variables — but only when the name arrives via `${…}`.
3. Exact match in the object table (including imports).
4. Pattern match against a declared object family.
5. Otherwise → **unresolved reference** (`GL201`).

Names arriving through `${…}` are **dynamic**: they cannot be resolved statically in general. The linter should mark them *unknown* rather than *unresolved*, and suppress `GL201` for them — except in the common, statically analysable case `menu_item-${index}` inside `@for [1 - 9]`, where the family `menu_item-*` can be checked.

---

## 18. Keyword / token tables

### 18.1 Statement keywords (all `@`-prefixed) — 14 total **[S]**

The authoritative set, from `MacroProcessor`'s dispatch table:

`@objects` · `@groups` · `@set` · `@script` · `@import` · **`@lib`** · `@rule` · `@ruleBody` · `@on` · `@if` · `@elseif` · `@else` · `@for` · `@forEach` · `@die`

`@lib` is real but **undocumented** (see §14.1). Anything else beginning with `@` is a syntax error.

`@elseif` / `@else` outside an `@if` chain raise `SyntaxException("elseif statement without if block")` **[S]**.

Plus the `@objects`-local forms: `@(...)` (correction), `@grouped(...)`.

Case sensitivity: `@forEach` is camelCase **[D]**, so keywords are **case-sensitive** **[I]**.

### 18.2 Spec names — 21 total **[S]**

The authoritative set, from `SpecReader`'s registration map:

`near` · `above` · `below` · `left-of` · `right-of` · `inside` · `width` · `height` · `aligned` · `text` · `css` · `centered` · `absent` · `visible` · `contains` · `on` · `component` · `count` · `ocr` · `color-scheme` · `image`

Galen shares one processor across related specs, which a parser can exploit directly:

| Processor | Specs |
|---|---|
| `SpecWithRangeProcessor` | `width`, `height` |
| `SpecWithObjectAndRangeProcessor` | `above`, `below`, `left-of`, `right-of` |
| `SingleWordSpecProcessor` | `absent`, `visible` |
| dedicated | the remaining 13 |

### 18.3 Contextual keywords

| Group | Words |
|---|---|
| Locator types | `id`, `css`, `xpath` |
| Sides | `left`, `right`, `top`, `bottom` |
| Alignment direction | `horizontally`, `vertically` |
| Alignment edge | `all`, `top`, `bottom`, `left`, `right`, `centered` |
| Centered direction | `horizontally`, `vertically`, `all` |
| Centered relation | `inside`, `on` |
| Text matchers | `is`, `contains`, `starts`, `ends`, `matches` |
| Text operations | `lowercase`, `uppercase`, `singleline` |
| Modifiers | `partly` (on `inside`, `contains`), `frame` (on `component`) |
| Count filters | `any`, `visible`, `absent` |
| Range | `to`, `of`, `px`, `%`, `>`, `<`, `>=`, `<=`, `~` |
| Relative properties | `width`, `height` (after `objectName/`) |
| Image options | `file`, `error`, `tolerance`, `stretch`, `area`, `analyze-offset`, `crop-if-outside`, `ignore-objects`, `filter`, `filter-a`, `filter-b`, `map-filter` |
| Image filters | `blur`, `saturation`, `contrast`, `denoise`, `quantinize`, `mask`, `replace-colors`, `with`, `radius` |
| Loop bindings | `as`, `next`, `prev`, `index` |
| Tag wildcard | `*` |

Note `contains`, `visible`, `absent`, `on`, `inside`, `width`, `height`, `all`, `left`, `right`, `top`, `bottom` are **each used in more than one role**. Do not build a single reserved-word set — resolve by position within a spec.

### 18.4 Version gating (for a `target-version` lint option)

| Feature | Introduced |
|---|---|
| `%` warning-level prefix | 1.2 |
| `left-of`, `right-of` | 1.6 |
| Custom rules (`@rule`, `\|`) | 1.6 |
| Spec notes (`"…" spec`) | 1.6 |
| New 2.0 syntax (whole language) | 2.0 — **not backwards compatible with 1.x** |
| `count` spec | 2.1 |
| Object groups (`@groups`, `&`, `@grouped`) | 2.2 |
| Rule bodies (`@ruleBody`, `doRuleBody`) | 2.2 |
| `@die` | 2.3 |
| Gradient `color-scheme` | 2.3 |
| `ocr` spec | 2.4 |

---

## 19. Known documentation inconsistencies

These are errors or ambiguities **in the official guide itself**. Do not encode them as grammar without checking the reference parser.

1. **`aligned` without an edge keyword.** The *Special Objects → screen* example is `aligned horizontally screen`, omitting the required `all|top|bottom|centered`. Either the edge is optional (defaulting to something) or the example is wrong. **Verify.**
2. **`near` without sides.** The component example has `near user-pic 10px` with no side keyword, while §Near always shows sides. Either sides are optional on `near` or the example is wrong. **Verify.**
3. **Missing colon in a rule-body example.** `banner` followed by an indented `width 1000px` — every other object statement uses `banner:`. Almost certainly a typo in the docs.
4. **Spec list is incomplete.** The "Galen supports the following specs" bullet list omits `visible`, `count`, `ocr`, and `image`, all of which are documented in their own sections. Use the full list in §18.2.
5. **Fenced language tag typo.** One code block in the source HTML is tagged `gale-specs` rather than `galen-specs` — irrelevant to the language, noted only because it affects scraping.
6. **`@objects` vs. top-level object statements.** The `@script i18n.function.js` example places `greeting-text:` at top level with no enclosing `= Section =`. So sections are optional. Confirm whether a spec outside any section is reported correctly.
7. **`count` on non-`global` objects.** The docs place `count` under `global:` but never say it is required there.
8. **Colour-name vocabulary for `color-scheme`** is never enumerated.

---

## 20. Proposed lint rule catalogue

Grouped by phase, with suggested default severities. IDs are a proposal, not from Galen.

### GL0xx — Lexical / formatting

| ID | Rule | Default |
|---|---|---|
| GL001 | Mixed tabs and spaces for indentation | warn |
| GL002 | Inconsistent indent width within a file | warn |
| GL003 | Trailing whitespace | info |
| GL004 | Line exceeds configured max length | info |
| GL005 | Object name at start of line begins with `#` (silently becomes a comment) | error |
| GL006 | File does not end with a newline | info |

### GL1xx — Syntax / structure

| ID | Rule | Default |
|---|---|---|
| GL101 | Unknown `@` statement | error |
| GL102 | Block-opening statement with an empty body | error |
| GL103 | `@elseif` / `@else` without a matching `@if` | error |
| GL104 | Unexpected dedent / indentation does not match any enclosing level | error |
| GL105 | Object statement missing the trailing `:` | error |
| GL106 | Unterminated string literal or `${…}` block | error |
| GL107 | Section header not closed with `=` | error |
| GL108 | Spec line outside any object statement | error |
| GL109 | `@objects` entry with no locator | error |

### GL2xx — Name resolution

| ID | Rule | Default |
|---|---|---|
| GL201 | Reference to an undeclared object | error |
| GL202 | Reference to an undeclared group (`&name`) | error |
| GL203 | Declared object never referenced in any spec | warn |
| GL204 | Loop variable declared but never used | warn |
| GL205 | Reference to an undefined `${variable}` | warn |
| GL206 | Duplicate object definition (same name declared twice) | error |
| GL207 | Object name shadows a special object (`screen`, `viewport`, `parent`, `self`, `global`) | warn |
| GL208 | Group contains an object that no longer exists | error |
| GL209 | Empty group | warn |

### GL3xx — Spec argument validation

| ID | Rule | Default |
|---|---|---|
| GL301 | Unknown spec name | error |
| GL302 | Invalid `aligned` direction/edge pairing (e.g. `aligned horizontally left`) | error |
| GL303 | Contradictory specs on one object (`absent` + a positional spec; `absent` + `visible`) | error |
| GL304 | Malformed range (missing unit, bad operator, `to` with one operand) | error |
| GL305 | `text matches` / `css matches` / `ocr text matches` argument is not a valid **Java** regex | error |
| GL306 | `lowercase`/`uppercase` used with the `css` spec (not supported) | error |
| GL307 | Duplicate side keyword in one side group (`10px left left`) | warn |
| GL308 | Contradictory sides in one group (`10px left right` is fine; `top top` is not) | warn |
| GL309 | `count` range given a `px` unit | error |
| GL310 | `image contrast` level outside 0–258 | error |
| GL311 | `denoise` used as `filter`/`filter-a`/`filter-b` instead of `map-filter` | error |
| GL312 | `image` spec with no `file` option | error |
| GL313 | `image area` without exactly four values | error |
| GL314 | `on` spec corner is not a valid `top\|bottom` + `left\|right` pair | error |
| GL315 | Relative range property is not `width` or `height` | error |
| GL316 | Duplicate identical spec on the same object | warn |
| GL317 | Object correction does not have exactly four values | error |

### GL4xx — Style / best practice (from explicit doc guidance)

| ID | Rule | Default |
|---|---|---|
| GL401 | `inside` without side keywords — docs: *"always specify sides"* | warn |
| GL402 | Use of the `css` spec — docs: *"use rarely and wisely"* | off by default |
| GL403 | `inside partly` with a negative range — docs: *"try to avoid; use `on` instead"* | warn |
| GL404 | `%` warning-level spec left in the file (technical debt marker) | info |
| GL405 | Section with no specs | warn |
| GL406 | Magic pixel value repeated N+ times — suggest `@set` | info |
| GL407 | Object declared with `id` locator but the locator string looks like CSS/XPath | warn |
| GL408 | Deeply nested sections (configurable depth) | info |

### GL5xx — Files & imports

| ID | Rule | Default |
|---|---|---|
| GL501 | `component` / `@import` / `@script` target file does not exist | **warn** — a path may legitimately resolve to a classpath resource **[S]** |
| GL502 | Circular `@import` chain | **warn** — Galen dedupes by file id and silently skips the revisit, so this never fails at run time **[S]** |
| GL503 | `image file` path does not exist (glob patterns: no match) | warn |
| GL504 | `image mask` file does not exist | warn |
| GL505 | Duplicate `@import` of the same file | warn — the second is silently skipped, i.e. dead code **[S]** |
| GL506 | Object name collision between a file and its import | warn |
| GL507 | `@lib` names a library not in the Galen distribution's `libs.list` | warn |

### GL6xx — Rules

| ID | Rule | Default |
|---|---|---|
| GL601 | Rule invocation is ambiguous — matches two or more `@rule` texts | error |
| GL602 | Rule invocation matches no known rule | error (warn if any `@script` is present) |
| GL603 | `@ruleBody` used outside a `@rule` definition | error |
| GL604 | Rule invocation supplies a body, but the rule never invokes `@ruleBody` | warn |
| GL605 | `@rule` defined but never invoked | warn |
| GL606 | `%{param: regex}` custom regex is invalid | error |
| GL607 | Rule body references a `${param}` not declared in the rule text | error |

### GL7xx — Embedded JavaScript

| ID | Rule | Default |
|---|---|---|
| GL701 | `.name` called as a function (`find("x").name()`) | error |
| GL702 | `${…}` contains a JS syntax error | error |
| GL703 | Unknown Galen JS API function called | warn |
| GL704 | `${…}` with unbalanced braces | error |

---

## 21. Suggested linter architecture

```
                     ┌──────────────┐
   *.gspec ────────► │  Lexer       │  indentation-aware, context-sensitive
                     │  (INDENT/    │  emits raw ${…} and @script blocks as
                     │   DEDENT)    │  opaque tokens
                     └──────┬───────┘
                            ▼
                     ┌──────────────┐
                     │  Parser      │  recursive descent per §3
                     │  → CST/AST   │  every node carries file/line/col span
                     └──────┬───────┘
                            ▼
             ┌──────────────┴──────────────┐
             ▼                             ▼
   ┌──────────────────┐          ┌────────────────────┐
   │ Import resolver  │          │ JS extractor       │
   │ builds module    │          │ ${…} + @script     │
   │ graph, detects   │          │ → JS parser        │
   │ cycles           │          │ (rule() discovery) │
   └────────┬─────────┘          └─────────┬──────────┘
            └───────────┬──────────────────┘
                        ▼
              ┌────────────────────┐
              │ Symbol tables (§17)│  objects, groups, vars, rules, tags
              └─────────┬──────────┘
                        ▼
              ┌────────────────────┐
              │ Rule engine        │  GL0xx…GL7xx, each a visitor
              └─────────┬──────────┘
                        ▼
              ┌────────────────────┐
              │ Reporters          │  CLI text, JSON, SARIF, LSP diagnostics
              └────────────────────┘
```

Design notes:

- **Error recovery matters.** For editor/LSP use, a parse error on one line must not abandon the file — recover at the next line whose indentation is ≤ the current block's.
- **Keep a lossless CST** if autofix (`--fix`) is a goal: formatting rules (GL001–GL004) and simple fixes (removing duplicate specs, normalising `100px` vs `100 px`) need exact source spans.
- **Two-pass semantics.** Objects can be referenced before they are declared (`@objects` is conventionally first, but sections may precede it, and imports merge in), so collect all declarations before resolving references.
- **Make dynamic-name handling explicit.** A three-valued resolution result — `Resolved` / `Unresolved` / `Dynamic` — prevents `${…}`-heavy files from drowning in false positives.
- **Version targeting.** Take a `galenVersion` config option and use §18.4 to flag features newer than the target.
- **Do not hard-fail on [?] items.** Ship them as warnings behind config flags until verified against `galenframework/galen`.

### Verification backlog

**Resolved from source [S]** — no longer open:

- ~~Tab handling~~ → tab = 4 columns; siblings must match exactly (§2.1).
- ~~`@import` path resolution~~ → relative to the importing file's parent dir, classpath resources supported, repeats and cycles deduped by file id (§14).
- ~~Complete statement and spec keyword sets~~ → 14 and 21 respectively (§18.1, §18.2); `@lib` discovered (§14.1).
- ~~`@elseif`/`@else` without `@if`~~ → a real `SyntaxException`.
- Variable names validate as `[a-zA-Z_][a-zA-Z0-9_]*` — narrower than object names, which allow `-` and `.` (§13).

**Still open** — confirm before turning any of these into a hard error:

1. `${…}` brace-matching algorithm as Galen implements it (nested braces, braces inside JS strings). GalenLinter currently scans balanced braces and skips quoted runs; verify against `VarsParser`.
2. Whether `near` sides and `aligned` edges are truly optional (§19 items 1–2) — check `SpecNearProcessor` / `SpecAlignedProcessor` and `ExpectSides`.
3. Object-name collision behaviour across imports.
4. Whether `@grouped(...)` accepts multiple comma-separated groups, and its ordering vs. `@(…)`.
5. Legal object-name character set.
6. Whether text operations can be chained.
7. Whether `count` is restricted to `global:`.
8. Decimal support in ranges (`ExpectRange` / `ExpectNumber`).
9. Ordering of `%` and `"note"` on a single spec line.
10. Whether nested `@on` is legal.
