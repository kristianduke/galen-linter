package com.galenlinter.highlight

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Color

/**
 * Checks what each colour key *actually paints*, by asking the live colour scheme.
 *
 * This is as close as a headless test can get to looking at the editor. It cannot judge whether a
 * colour is pleasant, but it can prove two constructs are not painted identically — which is the
 * concrete half of "the JavaScript colours conflict with the rest of the highlighting".
 */
class GalenPaletteTest : BasePlatformTestCase() {

    private fun colourOf(key: TextAttributesKey): Color? =
        EditorColorsManager.getInstance().globalScheme.getAttributes(key)?.foregroundColor

    private val galenPalette: Map<String, TextAttributesKey> = mapOf(
        "spec name" to GalenColors.SPEC_NAME,
        "statement" to GalenColors.STATEMENT,
        "section" to GalenColors.SECTION,
        "object ref" to GalenColors.OBJECT_REF,
        "object header" to GalenColors.OBJECT_HEADER,
        "string" to GalenColors.STRING,
        "number" to GalenColors.NUMBER,
        "locator" to GalenColors.LOCATOR,
        "expression" to GalenColors.EXPRESSION,
    )

    private val jsPalette: Map<String, TextAttributesKey> = mapOf(
        "js keyword" to GalenColors.JS_KEYWORD,
        "js string" to GalenColors.JS_STRING,
        "js number" to GalenColors.JS_NUMBER,
        "js api" to GalenColors.JS_GALEN_API,
        "js comment" to GalenColors.JS_COMMENT,
    )

    /**
     * The reported problem, stated precisely: a JavaScript colour must not be the same ink as a
     * Galen colour, or the two languages are indistinguishable where they meet.
     */
    fun testJavaScriptPaletteDoesNotCollideWithGalen() {
        val galen = galenPalette.mapNotNull { (name, key) -> colourOf(key)?.let { name to it } }
        assertTrue("Expected Galen colours to resolve", galen.isNotEmpty())

        val collisions = mutableListOf<String>()
        for ((jsName, jsKey) in jsPalette) {
            val jsColour = colourOf(jsKey) ?: continue
            for ((galenName, galenColour) in galen) {
                // A comment being grey in both languages is fine and expected.
                if (jsName == "js comment") continue
                if (jsColour == galenColour) collisions += "$jsName is the same ink as $galenName"
            }
        }

        assertTrue(
            "Embedded JavaScript should read as its own language: ${collisions.distinct()}",
            collisions.isEmpty(),
        )
    }

    /** The JavaScript colours must also differ from each other, or the palette says nothing. */
    fun testJavaScriptPaletteIsInternallyDistinct() {
        val resolved = jsPalette.mapNotNull { (name, key) -> colourOf(key)?.let { name to it } }
        assertTrue("Expected the JavaScript palette to resolve", resolved.size >= 4)

        val duplicates = resolved
            .groupBy { it.second }
            .filterValues { it.size > 1 }
            .map { (colour, names) -> "${names.map { it.first }} all paint $colour" }

        assertTrue("JavaScript colours should be distinguishable: $duplicates", duplicates.isEmpty())
    }


    /**
     * Constructs that must not share ink with each other.
     *
     * These pairs are ones a reader has to tell apart at a glance, and each has actually collided:
     * moving section headers onto METADATA to make them visible (0.6.0) gave them the same colour
     * as `${...}` expressions, and the "distinct" object statement header added in 0.4.0 resolved
     * to the same purple as an ordinary reference, differing only by italics.
     */
    fun testConstructsThatMustBeDistinguishableAre() {
        val mustDiffer = listOf(
            "section" to "expression",
            "object header" to "object ref",
            "spec name" to "object ref",
            "string" to "number",
        )

        val collisions = mustDiffer.mapNotNull { (a, b) ->
            val first = galenPalette[a]?.let { colourOf(it) }
            val second = galenPalette[b]?.let { colourOf(it) }
            if (first != null && first == second) "$a and $b both paint $first" else null
        }

        assertTrue("These should be distinguishable at a glance: $collisions", collisions.isEmpty())
    }

    /**
     * Prints the resolved palette. Not an assertion — a way to inspect the actual inks from a
     * headless run, since the rendered editor cannot be seen from here.
     */
    fun testReportResolvedPalette() {
        val scheme = EditorColorsManager.getInstance().globalScheme
        println("PALETTE scheme=${scheme.name}")
        for ((name, key) in galenPalette + jsPalette) {
            val attributes = scheme.getAttributes(key)
            val fg = attributes?.foregroundColor
            println(
                "PALETTE %-16s %-26s %s".format(
                    name,
                    key.externalName,
                    fg?.let { "#%02x%02x%02x".format(it.red, it.green, it.blue) } ?: "none",
                ),
            )
        }
    }
}
