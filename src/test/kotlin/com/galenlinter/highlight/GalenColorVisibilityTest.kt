package com.galenlinter.highlight

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Guards the whole class of bug that produced three separate reports.
 *
 * A colour key can be perfectly correct and still paint nothing: `FUNCTION_CALL` and `CLASS_NAME`
 * both fall through to `IDENTIFIER` in the bundled schemes, so anything relying on them renders as
 * plain text. Nothing else in the suite can see that — the element is parsed, annotated and
 * "coloured", just invisibly.
 *
 * So this asks the real colour scheme what each key actually resolves to, rather than trusting the
 * fallback chain to end somewhere useful.
 */
class GalenColorVisibilityTest : BasePlatformTestCase() {

    /**
     * Keys that are deliberately unstyled: they mark something that should look like ordinary text
     * or ordinary punctuation.
     */
    private val intentionallyPlain = setOf(
        "GALEN_UNKNOWN_SPEC",
        "GALEN_PUNCTUATION",
        "GALEN_BRACKETS",
        "GALEN_OPERATOR",
    )

    private fun allKeys(): List<TextAttributesKey> = listOf(
        GalenColors.STATEMENT, GalenColors.SECTION, GalenColors.SPEC_NAME,
        GalenColors.OBJECT_HEADER, GalenColors.UNKNOWN_SPEC, GalenColors.OBJECT_REF,
        GalenColors.LOCATOR, GalenColors.EXPRESSION, GalenColors.RULE_PARAM,
        GalenColors.CORRECTION, GalenColors.WARNING_PREFIX, GalenColors.COMMENT,
        GalenColors.STRING, GalenColors.NUMBER, GalenColors.OPERATOR,
        GalenColors.PUNCTUATION, GalenColors.BRACKETS, GalenColors.SIDE,
        GalenColors.MATCHER, GalenColors.TEXT_OPERATION, GalenColors.ALIGN_KEYWORD,
        GalenColors.MODIFIER, GalenColors.UNIT, GalenColors.LOCATOR_TYPE,
        GalenColors.SPECIAL_OBJECT, GalenColors.PROPERTY_NAME, GalenColors.IMAGE_OPTION,
        GalenColors.FILE_PATH, GalenColors.COLOR_VALUE, GalenColors.GROUP_REF,
    )

    fun testEveryColourKeyActuallyRendersVisibly() {
        val scheme = EditorColorsManager.getInstance().globalScheme
        val invisible = allKeys()
            .filterNot { it.externalName in intentionallyPlain }
            .filter { key ->
                val attributes = scheme.getAttributes(key)
                // No colour and no font style means the scheme paints it as ordinary text.
                attributes == null ||
                    (attributes.foregroundColor == null &&
                        attributes.fontType == 0 &&
                        attributes.effectType == null)
            }
            .map { it.externalName }

        assertTrue(
            "These colour keys resolve to nothing the scheme paints, so the construct will look " +
                "unhighlighted: $invisible",
            invisible.isEmpty(),
        )
    }

    /** The specific keys behind the three reports, pinned individually. */
    fun testPreviouslyInvisibleKeysAreNowVisible() {
        val scheme = EditorColorsManager.getInstance().globalScheme
        for (key in listOf(GalenColors.SPEC_NAME, GalenColors.SECTION, GalenColors.OBJECT_HEADER)) {
            val attributes = scheme.getAttributes(key)
            assertNotNull("${key.externalName} has no attributes at all", attributes)
            assertTrue(
                "${key.externalName} must resolve to a visible colour or style",
                attributes!!.foregroundColor != null || attributes.fontType != 0,
            )
        }
    }
}
