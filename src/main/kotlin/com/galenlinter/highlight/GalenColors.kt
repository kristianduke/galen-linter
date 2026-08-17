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
}
