package com.galenlinter.highlight

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Default
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey

object GalenColors {
    val STATEMENT: TextAttributesKey =
        createTextAttributesKey("GALEN_STATEMENT", Default.KEYWORD)

    /**
     * Section headers.
     *
     * Not CLASS_NAME: `DEFAULT_CLASS_NAME` has no entry at all in the bundled colour schemes, so it
     * falls through to IDENTIFIER and renders as plain text. METADATA carries a real foreground in
     * both the light and dark schemes.
     */
    val SECTION: TextAttributesKey =
        createTextAttributesKey("GALEN_SECTION", Default.METADATA)

    /**
     * Spec names are the language's verbs — `visible`, `width`, `inside` — so they fall back to
     * KEYWORD.
     *
     * They previously fell back to FUNCTION_CALL, which itself falls back to IDENTIFIER, meaning
     * colour schemes render it as plain default text: recognised, but visually indistinguishable
     * from an unknown word.
     */
    val SPEC_NAME: TextAttributesKey =
        createTextAttributesKey("GALEN_SPEC_NAME", Default.KEYWORD)

    /**
     * The `objectName:` that opens a block of specs.
     *
     * Distinct from a plain object reference: it is the anchor the specs below it hang off, and it
     * is what the eye scans for when reading a spec file.
     */
    /**
     * Distinguished from a plain object reference by style rather than hue: CONSTANT is the same
     * purple family as INSTANCE_FIELD but italic, in both bundled schemes. FUNCTION_DECLARATION,
     * used previously, has no entry in the light scheme.
     */
    val OBJECT_HEADER: TextAttributesKey =
        createTextAttributesKey("GALEN_OBJECT_HEADER", Default.CONSTANT)

    val UNKNOWN_SPEC: TextAttributesKey =
        createTextAttributesKey("GALEN_UNKNOWN_SPEC", Default.IDENTIFIER)

    val OBJECT_REF: TextAttributesKey =
        createTextAttributesKey("GALEN_OBJECT_REF", Default.INSTANCE_FIELD)

    val LOCATOR: TextAttributesKey =
        createTextAttributesKey("GALEN_LOCATOR", Default.STRING)

    val EXPRESSION: TextAttributesKey =
        createTextAttributesKey("GALEN_EXPRESSION", Default.METADATA)

    val RULE_PARAM: TextAttributesKey =
        createTextAttributesKey("GALEN_RULE_PARAM", Default.METADATA)

    val CORRECTION: TextAttributesKey =
        createTextAttributesKey("GALEN_CORRECTION", Default.METADATA)

    /** The `%` prefix that downgrades a failing spec to a warning. */
    // LABEL has no foreground in either bundled scheme.
    val WARNING_PREFIX: TextAttributesKey =
        createTextAttributesKey("GALEN_WARNING_PREFIX", Default.METADATA)

    val COMMENT: TextAttributesKey =
        createTextAttributesKey("GALEN_COMMENT", Default.LINE_COMMENT)

    val STRING: TextAttributesKey =
        createTextAttributesKey("GALEN_STRING", Default.STRING)

    val NUMBER: TextAttributesKey =
        createTextAttributesKey("GALEN_NUMBER", Default.NUMBER)

    val OPERATOR: TextAttributesKey =
        createTextAttributesKey("GALEN_OPERATOR", Default.OPERATION_SIGN)

    val PUNCTUATION: TextAttributesKey =
        createTextAttributesKey("GALEN_PUNCTUATION", Default.COMMA)

    val BRACKETS: TextAttributesKey =
        createTextAttributesKey("GALEN_BRACKETS", Default.BRACKETS)

    // ---- contextual keywords ----------------------------------------------
    // The lexer cannot reach any of these: they are all bare words, told apart only by position
    // in the grammar. GalenAnnotator applies them from the parse tree.

    /** `left`, `right`, `top`, `bottom`. */
    val SIDE: TextAttributesKey =
        createTextAttributesKey("GALEN_SIDE", Default.KEYWORD)

    /** `is`, `contains`, `starts`, `ends`, `matches`. */
    val MATCHER: TextAttributesKey =
        createTextAttributesKey("GALEN_MATCHER", Default.KEYWORD)

    /** `lowercase`, `uppercase`, `singleline`. */
    val TEXT_OPERATION: TextAttributesKey =
        createTextAttributesKey("GALEN_TEXT_OPERATION", Default.KEYWORD)

    /** `horizontally`, `vertically`, and the alignment edges. */
    val ALIGN_KEYWORD: TextAttributesKey =
        createTextAttributesKey("GALEN_ALIGN_KEYWORD", Default.KEYWORD)

    /** `partly`, `frame`, `edge`, `any`/`visible`/`absent` on `count`. */
    val MODIFIER: TextAttributesKey =
        createTextAttributesKey("GALEN_MODIFIER", Default.KEYWORD)

    /** `px`, `%`, and the `to` / `of` range words. */
    val UNIT: TextAttributesKey =
        createTextAttributesKey("GALEN_UNIT", Default.NUMBER)

    /** `id`, `css`, `xpath` in an `@objects` entry. */
    val LOCATOR_TYPE: TextAttributesKey =
        createTextAttributesKey("GALEN_LOCATOR_TYPE", Default.KEYWORD)

    /**
     * `screen`, `viewport`, `parent`, `self`, `global`.
     *
     * Worth its own colour: these are the only object names that need no declaration, so seeing
     * one *not* highlighted is an immediate signal that a name is misspelled.
     */
    // KEYWORD rather than PREDEFINED_SYMBOL, which carries no foreground in either bundled
    // scheme. These are language built-ins needing no declaration, like `this` in Java.
    val SPECIAL_OBJECT: TextAttributesKey =
        createTextAttributesKey("GALEN_SPECIAL_OBJECT", Default.KEYWORD)

    /** The `width` / `height` tail of a relative range. */
    val PROPERTY_NAME: TextAttributesKey =
        createTextAttributesKey("GALEN_PROPERTY_NAME", Default.INSTANCE_FIELD)

    /** `file`, `error`, `tolerance`, `stretch`, image filter names, ... */
    val IMAGE_OPTION: TextAttributesKey =
        createTextAttributesKey("GALEN_IMAGE_OPTION", Default.KEYWORD)

    /** A file path in `@import`, `component`, `image file`, ... */
    val FILE_PATH: TextAttributesKey =
        createTextAttributesKey("GALEN_FILE_PATH", Default.STRING)

    /** A colour name or hex value in `color-scheme`. */
    val COLOR_VALUE: TextAttributesKey =
        createTextAttributesKey("GALEN_COLOR_VALUE", Default.NUMBER)

    /** `&groupName`. */
    val GROUP_REF: TextAttributesKey =
        createTextAttributesKey("GALEN_GROUP_REF", Default.STATIC_FIELD)

    // ---- embedded JavaScript ----------------------------------------------
    // Applied only where no JavaScript plugin is installed; otherwise injection colours it
    // properly. All fall back to keys the bundled schemes actually paint.

    val JS_KEYWORD: TextAttributesKey =
        createTextAttributesKey("GALEN_JS_KEYWORD", Default.KEYWORD)

    val JS_STRING: TextAttributesKey =
        createTextAttributesKey("GALEN_JS_STRING", Default.STRING)

    val JS_NUMBER: TextAttributesKey =
        createTextAttributesKey("GALEN_JS_NUMBER", Default.NUMBER)

    val JS_COMMENT: TextAttributesKey =
        createTextAttributesKey("GALEN_JS_COMMENT", Default.LINE_COMMENT)

    /** `count`, `find`, `findAll`, `isVisible`, `isPresent`, `viewport`, `screen`. */
    val JS_GALEN_API: TextAttributesKey =
        createTextAttributesKey("GALEN_JS_API", Default.INSTANCE_FIELD)

    val JS_IDENTIFIER: TextAttributesKey =
        createTextAttributesKey("GALEN_JS_IDENTIFIER", Default.IDENTIFIER)

    val JS_PUNCTUATION: TextAttributesKey =
        createTextAttributesKey("GALEN_JS_PUNCTUATION", Default.OPERATION_SIGN)
}
