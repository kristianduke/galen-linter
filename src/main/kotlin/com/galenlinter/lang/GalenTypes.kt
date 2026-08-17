package com.galenlinter.lang

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

class GalenTokenType(debugName: String) : IElementType(debugName, GalenLanguage) {
    override fun toString(): String = "Galen:" + super.toString()
}

class GalenElementType(debugName: String) : IElementType(debugName, GalenLanguage)

/**
 * Token registry.
 *
 * The lexer is deliberately context-free and line-local: it never needs to know which block
 * it is inside. That keeps [com.galenlinter.lexer.GalenLexer] restartable (its whole state fits
 * in an `Int`), which is what makes IntelliJ's incremental re-lexing work. All context-sensitive
 * interpretation — "this run of tokens is a CSS locator", "this word is a spec name" — is done by
 * the parser and the annotator instead.
 */
object GalenTypes {

    // ---- structural -------------------------------------------------------
    /**
     * Leading whitespace of a line. Deliberately NOT part of [GalenParserDefinition]'s whitespace
     * token set: the parser reads its text to compute indentation depth (tab = 4, per Galen's
     * IndentationStructureParser.TAB_SIZE).
     */
    @JvmField val LINE_INDENT = GalenTokenType("LINE_INDENT")
    @JvmField val EOL = GalenTokenType("EOL")
    @JvmField val COMMENT = GalenTokenType("COMMENT")

    // ---- spans scanned as single tokens -----------------------------------
    /** `${ ... }` — scanned with balanced-brace matching, so `${ {a:1}.a }` survives. */
    @JvmField val EXPRESSION = GalenTokenType("EXPRESSION")

    /** `%{name}` or `%{name: regex}` — a custom-rule parameter capture. */
    @JvmField val RULE_PARAM = GalenTokenType("RULE_PARAM")

    /** `@(0, 0, -50, 0)` — an object correction. */
    @JvmField val CORRECTION = GalenTokenType("CORRECTION")

    /** A double-quoted string: a spec note, a text/css expectation, or a `@die` message. */
    @JvmField val STRING = GalenTokenType("STRING")

    /** Any `@word` statement keyword. The parser validates it against the known set. */
    @JvmField val AT_KEYWORD = GalenTokenType("AT_KEYWORD")

    @JvmField val NUMBER = GalenTokenType("NUMBER")

    /**
     * A bare word: object name, spec name, locator fragment, side keyword, unit, colour, ...
     * Disambiguated positionally by the parser.
     */
    @JvmField val WORD = GalenTokenType("WORD")

    // ---- punctuation ------------------------------------------------------
    @JvmField val COLON = GalenTokenType("COLON")
    @JvmField val COMMA = GalenTokenType("COMMA")
    @JvmField val PIPE = GalenTokenType("PIPE")
    @JvmField val AMP = GalenTokenType("AMP")
    @JvmField val EQ = GalenTokenType("EQ")
    @JvmField val SLASH = GalenTokenType("SLASH")
    @JvmField val PERCENT = GalenTokenType("PERCENT")
    @JvmField val DOLLAR = GalenTokenType("DOLLAR")
    @JvmField val LBRACKET = GalenTokenType("LBRACKET")
    @JvmField val RBRACKET = GalenTokenType("RBRACKET")
    @JvmField val LPAREN = GalenTokenType("LPAREN")
    @JvmField val RPAREN = GalenTokenType("RPAREN")
    @JvmField val LBRACE = GalenTokenType("LBRACE")
    @JvmField val RBRACE = GalenTokenType("RBRACE")
    @JvmField val LT = GalenTokenType("LT")
    @JvmField val GT = GalenTokenType("GT")
    @JvmField val LE = GalenTokenType("LE")
    @JvmField val GE = GalenTokenType("GE")
    @JvmField val TILDE = GalenTokenType("TILDE")
    @JvmField val PLUS = GalenTokenType("PLUS")
    @JvmField val MINUS = GalenTokenType("MINUS")
    @JvmField val SEMICOLON = GalenTokenType("SEMICOLON")

    // ---- composite (PSI) elements -----------------------------------------
    @JvmField val OBJECTS_BLOCK = GalenElementType("OBJECTS_BLOCK")
    @JvmField val OBJECT_DEF = GalenElementType("OBJECT_DEF")
    @JvmField val LOCATOR = GalenElementType("LOCATOR")
    @JvmField val GROUPS_BLOCK = GalenElementType("GROUPS_BLOCK")
    @JvmField val GROUP_DEF = GalenElementType("GROUP_DEF")
    @JvmField val SET_BLOCK = GalenElementType("SET_BLOCK")
    @JvmField val SET_ENTRY = GalenElementType("SET_ENTRY")
    @JvmField val SCRIPT_BLOCK = GalenElementType("SCRIPT_BLOCK")
    @JvmField val RAW_JS_LINE = GalenElementType("RAW_JS_LINE")
    @JvmField val IMPORT_STATEMENT = GalenElementType("IMPORT_STATEMENT")
    @JvmField val LIB_STATEMENT = GalenElementType("LIB_STATEMENT")
    @JvmField val RULE_DEFINITION = GalenElementType("RULE_DEFINITION")
    @JvmField val RULE_BODY_STATEMENT = GalenElementType("RULE_BODY_STATEMENT")
    @JvmField val RULE_INVOCATION = GalenElementType("RULE_INVOCATION")
    @JvmField val ON_STATEMENT = GalenElementType("ON_STATEMENT")
    @JvmField val IF_STATEMENT = GalenElementType("IF_STATEMENT")
    @JvmField val ELSEIF_STATEMENT = GalenElementType("ELSEIF_STATEMENT")
    @JvmField val ELSE_STATEMENT = GalenElementType("ELSE_STATEMENT")
    @JvmField val FOR_STATEMENT = GalenElementType("FOR_STATEMENT")
    @JvmField val FOREACH_STATEMENT = GalenElementType("FOREACH_STATEMENT")
    @JvmField val DIE_STATEMENT = GalenElementType("DIE_STATEMENT")
    @JvmField val SECTION = GalenElementType("SECTION")
    @JvmField val SECTION_TITLE = GalenElementType("SECTION_TITLE")
    @JvmField val OBJECT_STATEMENT = GalenElementType("OBJECT_STATEMENT")
    @JvmField val OBJECT_REF_LIST = GalenElementType("OBJECT_REF_LIST")
    // Object statement headers use OBJECT_NAME_REF / GROUP_REF, the same element types as spec
    // arguments, so navigation and unresolved-reference checking apply to both uniformly.
    @JvmField val SPEC_LINE = GalenElementType("SPEC_LINE")
    @JvmField val SPEC_NAME = GalenElementType("SPEC_NAME")
    @JvmField val SPEC_ARGS = GalenElementType("SPEC_ARGS")
    @JvmField val UNKNOWN_STATEMENT = GalenElementType("UNKNOWN_STATEMENT")

    // ---- spec argument elements -------------------------------------------
    // These exist so that the annotator can colour by role and so that references have
    // something to attach to. The lexer sees only bare words here; position in the grammar is
    // the only thing that distinguishes an object name from a side keyword.

    /** An object name used as an argument. Hosts the reference that gives ctrl+click. */
    @JvmField val OBJECT_NAME_REF = GalenElementType("OBJECT_NAME_REF")

    /**
     * The *declared* name in an `@objects` entry — the definition side of the same relationship.
     * Kept separate from [OBJECT_NAME_REF] so it can become the name identifier of a
     * `PsiNameIdentifierOwner`, which is what makes rename and find-usages work.
     */
    @JvmField val OBJECT_NAME = GalenElementType("OBJECT_NAME")

    /** `&groupName`. */
    @JvmField val GROUP_REF = GalenElementType("GROUP_REF")

    /** A path in `@import`, `@script`, `component`, `image file`, `filter mask`. */
    @JvmField val FILE_PATH_REF = GalenElementType("FILE_PATH_REF")

    @JvmField val RANGE = GalenElementType("RANGE")
    @JvmField val UNIT = GalenElementType("UNIT")

    /** `to` / `of` inside a range. */
    @JvmField val RANGE_KEYWORD = GalenElementType("RANGE_KEYWORD")

    /** `% of object/width` — the `object/property` tail. */
    @JvmField val RELATIVE_REF = GalenElementType("RELATIVE_REF")
    @JvmField val PROPERTY_NAME = GalenElementType("PROPERTY_NAME")

    @JvmField val SIDE = GalenElementType("SIDE")

    /** One `range side...` group; specs take a comma-separated list of them. */
    @JvmField val SIDE_GROUP = GalenElementType("SIDE_GROUP")

    @JvmField val MATCHER = GalenElementType("MATCHER")
    @JvmField val TEXT_OPERATION = GalenElementType("TEXT_OPERATION")
    @JvmField val CSS_PROPERTY = GalenElementType("CSS_PROPERTY")

    @JvmField val ALIGN_DIRECTION = GalenElementType("ALIGN_DIRECTION")
    @JvmField val ALIGN_EDGE = GalenElementType("ALIGN_EDGE")
    @JvmField val CENTERED_DIRECTION = GalenElementType("CENTERED_DIRECTION")
    @JvmField val CENTERED_RELATION = GalenElementType("CENTERED_RELATION")
    @JvmField val CORNER = GalenElementType("CORNER")
    @JvmField val COUNT_FILTER = GalenElementType("COUNT_FILTER")

    /** `partly`, `frame`, `edge` — small positional modifier words. */
    @JvmField val MODIFIER = GalenElementType("MODIFIER")

    @JvmField val ERROR_RATE = GalenElementType("ERROR_RATE")
    @JvmField val IMAGE_OPTION = GalenElementType("IMAGE_OPTION")
    @JvmField val IMAGE_FILTER = GalenElementType("IMAGE_FILTER")
    @JvmField val COLOR_ENTRY = GalenElementType("COLOR_ENTRY")
    @JvmField val COLOR_VALUE = GalenElementType("COLOR_VALUE")
    @JvmField val COMPONENT_ARG = GalenElementType("COMPONENT_ARG")
    @JvmField val LOCATOR_TYPE = GalenElementType("LOCATOR_TYPE")

    // ---- token sets -------------------------------------------------------
    @JvmField val COMMENTS: TokenSet = TokenSet.create(COMMENT)
    @JvmField val STRINGS: TokenSet = TokenSet.create(STRING)

    /**
     * The 14 statement keywords Galen actually dispatches on, taken from
     * `com.galenframework.speclang2.pagespec.MacroProcessor`.
     *
     * Note `@lib` is real but undocumented — it does not appear in the official
     * spec language guide.
     */
    @JvmField
    val STATEMENT_KEYWORDS: Set<String> = setOf(
        "@objects", "@groups", "@set", "@script", "@import", "@lib",
        "@rule", "@ruleBody", "@on", "@if", "@elseif", "@else",
        "@for", "@forEach", "@die",
    )

    /**
     * The 21 spec names Galen registers, taken from
     * `com.galenframework.speclang2.specs.SpecReader`.
     */
    @JvmField
    val SPEC_NAMES: Set<String> = setOf(
        "inside", "contains", "near", "aligned", "absent", "visible",
        "width", "height", "text", "css", "above", "below",
        "left-of", "right-of", "centered", "on", "color-scheme",
        "image", "component", "count", "ocr",
    )

    // ---- contextual keyword vocabularies ----------------------------------
    // Every set below is taken from Galen's source rather than its documentation; see
    // docs/galen-spec-reference.md for the file each one came from.

    /** `specs/Side.java` — exactly these four, matched case-sensitively. */
    @JvmField val SIDES: Set<String> = setOf("left", "right", "top", "bottom")

    @JvmField val LOCATOR_TYPES: Set<String> = setOf("id", "css", "xpath")

    @JvmField val SPECIAL_OBJECTS: Set<String> = setOf("screen", "viewport", "parent", "self", "global")

    @JvmField val ALIGN_DIRECTIONS: Set<String> = setOf("horizontally", "vertically")

    /**
     * `specs/SpecAlignedProcessor.java` — the edge is required, and only these pairings are legal.
     * Galen rejects the rest with "Incorrect side for <direction> alignment: <EDGE>".
     */
    @JvmField val ALIGN_EDGES_HORIZONTAL: Set<String> = setOf("all", "top", "bottom", "centered")
    @JvmField val ALIGN_EDGES_VERTICAL: Set<String> = setOf("all", "left", "right", "centered")
    @JvmField val ALIGN_EDGES: Set<String> = ALIGN_EDGES_HORIZONTAL + ALIGN_EDGES_VERTICAL

    /** Valid edges for a direction, or all of them when the direction is unknown/dynamic. */
    fun alignEdgesFor(direction: String?): Set<String> = when (direction) {
        "horizontally" -> ALIGN_EDGES_HORIZONTAL
        "vertically" -> ALIGN_EDGES_VERTICAL
        else -> ALIGN_EDGES
    }

    @JvmField val CENTERED_DIRECTIONS: Set<String> = setOf("horizontally", "vertically", "all")
    @JvmField val CENTERED_RELATIONS: Set<String> = setOf("inside", "on")

    @JvmField val MATCHERS: Set<String> = setOf("is", "contains", "starts", "ends", "matches")

    /**
     * `specs/SpecTextProcessor.java` accumulates *any* unrecognised word as a text operation, so
     * a typo here parses cleanly and only misbehaves at run time. That makes this set a lint
     * vocabulary rather than a syntax rule — report unknown operations as warnings.
     */
    @JvmField val TEXT_OPERATIONS: Set<String> = setOf("lowercase", "uppercase", "singleline")

    /** `css` does not support case folding. */
    @JvmField val CSS_UNSUPPORTED_OPERATIONS: Set<String> = setOf("lowercase", "uppercase")

    @JvmField val COUNT_FILTERS: Set<String> = setOf("any", "visible", "absent")

    /** `parser/ExpectRange.java` — relative paths are not validated by Galen at parse time. */
    @JvmField val RELATIVE_PROPERTIES: Set<String> = setOf("width", "height")

    @JvmField val IMAGE_OPTIONS: Set<String> = setOf(
        "file", "error", "tolerance", "stretch", "area", "analyze-offset",
        "crop-if-outside", "ignore-objects", "filter", "filter-a", "filter-b", "map-filter",
    )

    @JvmField val IMAGE_FILTER_PREFIXES: Set<String> = setOf("filter", "filter-a", "filter-b", "map-filter")

    @JvmField val IMAGE_FILTERS: Set<String> = setOf(
        "blur", "saturation", "contrast", "denoise", "quantinize", "mask", "replace-colors",
    )

    /** `denoise` works on the black/white comparison map only. */
    @JvmField val MAP_ONLY_FILTERS: Set<String> = setOf("denoise")

    /** Specs whose first argument is an object name. */
    @JvmField val OBJECT_FIRST_SPECS: Set<String> = setOf(
        "near", "inside", "on", "above", "below", "left-of", "right-of",
    )
}
