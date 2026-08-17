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
    @JvmField val OBJECT_REF = GalenElementType("OBJECT_REF")
    @JvmField val SPEC_LINE = GalenElementType("SPEC_LINE")
    @JvmField val SPEC_NAME = GalenElementType("SPEC_NAME")
    @JvmField val SPEC_ARGS = GalenElementType("SPEC_ARGS")
    @JvmField val UNKNOWN_STATEMENT = GalenElementType("UNKNOWN_STATEMENT")

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
}
