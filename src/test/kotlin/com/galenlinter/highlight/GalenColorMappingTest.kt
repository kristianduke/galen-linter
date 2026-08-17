package com.galenlinter.highlight

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Colouring fails silently: an element can be parsed and annotated correctly and still render as
 * plain text if its colour key falls back to `IDENTIFIER`. These tests assert the mapping directly.
 */
class GalenColorMappingTest : BasePlatformTestCase() {

    private fun colourOf(text: String, target: String): TextAttributesKey? {
        myFixture.configureByText("test.gspec", text)
        var found: TextAttributesKey? = null
        fun walk(element: PsiElement) {
            if (element.text == target && found == null) {
                GalenColorMapping.attributesFor(element)?.let { found = it }
            }
            element.children.forEach(::walk)
        }
        walk(myFixture.file)
        return found
    }

    // ---- the reported bug --------------------------------------------------

    fun testSpecNamesAreColoured() {
        val body = "= Main =\n    target:\n        visible\n        contains other\n        width 100px\n"
        for (spec in listOf("visible", "contains", "width")) {
            val colour = colourOf(body, spec)
            assertEquals("'$spec' should be coloured as a spec name", GalenColors.SPEC_NAME, colour)
        }
    }

    /**
     * The regression that made spec names look uncoloured: FUNCTION_CALL falls back to IDENTIFIER,
     * which colour schemes render as plain text.
     */
    fun testSpecNameColourDoesNotFallBackToPlainIdentifier() {
        assertEquals(
            "Spec names must fall back to a key schemes actually style",
            DefaultLanguageHighlighterColors.KEYWORD,
            GalenColors.SPEC_NAME.fallbackAttributeKey,
        )
    }

    fun testObjectStatementHeaderIsColouredAndDistinctFromAReference() {
        // Each name appears once, so the first match is unambiguous. (A name declared in @objects
        // would match its declaration first, which is an OBJECT_NAME and coloured as a reference.)
        val body = "= Main =\n    hero-header:\n        below other 10px\n"

        // The header and the argument reference share an element type, so the mapping has to tell
        // them apart by position in the grammar.
        val header = colourOf(body, "hero-header")
        val reference = colourOf(body, "other")

        assertEquals(GalenColors.OBJECT_HEADER, header)
        assertEquals(GalenColors.OBJECT_REF, reference)
        assertNotSame("A header must not look like a plain reference", header, reference)
    }

    fun testObjectDeclarationIsColoured() =
        assertEquals(GalenColors.OBJECT_REF, colourOf("@objects\n    hero-header  #hero\n", "hero-header"))

    fun testGroupHeaderUsesTheHeaderColour() {
        val body = "@objects\n    a   #a\n\n@groups\n    skel   a\n\n= Main =\n    &skel:\n        visible\n"
        assertEquals(GalenColors.OBJECT_HEADER, colourOf(body, "&skel"))
    }

    // ---- keyword roles -----------------------------------------------------

    fun testSideKeywordsAreColoured() =
        assertEquals(
            GalenColors.SIDE,
            colourOf("= Main =\n    a:\n        near b 10px left\n", "left"),
        )

    fun testMatchersAndTextOperationsAreColoured() {
        val body = "= Main =\n    a:\n        text lowercase is \"x\"\n"
        assertEquals(GalenColors.TEXT_OPERATION, colourOf(body, "lowercase"))
        assertEquals(GalenColors.MATCHER, colourOf(body, "is"))
    }

    fun testAlignmentKeywordsAreColoured() {
        val body = "= Main =\n    a:\n        aligned horizontally all b\n"
        assertEquals(GalenColors.ALIGN_KEYWORD, colourOf(body, "horizontally"))
        assertEquals(GalenColors.ALIGN_KEYWORD, colourOf(body, "all"))
    }

    fun testSpecialObjectsHaveTheirOwnColour() =
        assertEquals(
            GalenColors.SPECIAL_OBJECT,
            colourOf("= Main =\n    a:\n        inside screen 0px top\n", "screen"),
        )

    fun testUnitsAndLocatorTypesAreColoured() {
        assertEquals(GalenColors.UNIT, colourOf("= Main =\n    a:\n        width 10 px\n", "px"))
        assertEquals(GalenColors.LOCATOR_TYPE, colourOf("@objects\n    a  css  .a\n", "css"))
    }

    fun testUnknownSpecUsesTheUnrecognisedColour() =
        assertEquals(
            GalenColors.UNKNOWN_SPEC,
            colourOf("= Main =\n    a:\n        widht 100px\n", "widht"),
        )

    /** A misspelled keyword is deliberately left uncoloured, which is a visible signal in itself. */
    fun testMisspelledSideIsNotColoured() =
        assertNull(colourOf("= Main =\n    a:\n        near b 10px lefft\n", "lefft"))
}
