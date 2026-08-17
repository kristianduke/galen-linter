package com.galenlinter.highlight

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Default
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey

object GalenColors {
    val STATEMENT: TextAttributesKey =
        createTextAttributesKey("GALEN_STATEMENT", Default.KEYWORD)

    val SECTION: TextAttributesKey =
        createTextAttributesKey("GALEN_SECTION", Default.CLASS_NAME)

    val SPEC_NAME: TextAttributesKey =
        createTextAttributesKey("GALEN_SPEC_NAME", Default.FUNCTION_CALL)

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
    val WARNING_PREFIX: TextAttributesKey =
        createTextAttributesKey("GALEN_WARNING_PREFIX", Default.LABEL)

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
    val SPECIAL_OBJECT: TextAttributesKey =
        createTextAttributesKey("GALEN_SPECIAL_OBJECT", Default.PREDEFINED_SYMBOL)

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
}
