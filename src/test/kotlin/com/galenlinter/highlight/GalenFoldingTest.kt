package com.galenlinter.highlight

import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class GalenFoldingTest : BasePlatformTestCase() {

    private fun foldsIn(text: String): List<FoldingDescriptor> {
        val file = myFixture.configureByText("test.gspec", text)
        val document = myFixture.getDocument(file)
        return GalenFoldingBuilder().buildFoldRegions(file, document, true).toList()
    }

    private fun placeholders(text: String): List<String> =
        foldsIn(text).map { GalenFoldingBuilder().getPlaceholderText(it.element) }

    fun testSectionAndObjectStatementFold() {
        val folds = foldsIn(
            """
            = Main section =
                header:
                    visible
                    width 100px
            """.trimIndent(),
        )
        val types = folds.map { it.element.elementType.toString() }
        assertTrue("Expected a section fold, got $types", types.any { it.contains("SECTION") })
        assertTrue("Expected an object statement fold, got $types", types.any { it.contains("OBJECT_STATEMENT") })
    }

    fun testObjectsBlockFoldsAndNestedObjectFolds() {
        val types = foldsIn(
            """
            @objects
                panel   #panel
                    input   input
                    button  a
            """.trimIndent(),
        ).map { it.element.elementType.toString() }

        assertTrue("Expected @objects fold, got $types", types.any { it.contains("OBJECTS_BLOCK") })
        // `panel` has nested children, so it folds too; `input` and `button` do not.
        assertEquals("Only the nested parent should fold", 1, types.count { it.contains("OBJECT_DEF") })
    }

    fun testControlFlowFolds() {
        val types = foldsIn(
            """
            = Main =
                @on mobile
                    header:
                        visible
                @if ${'$'}{isVisible("a")}
                    a:
                        visible
                @forEach [item-*] as i
                    ${'$'}{i}:
                        visible
            """.trimIndent(),
        ).map { it.element.elementType.toString() }

        assertTrue(types.any { it.contains("ON_STATEMENT") })
        assertTrue(types.any { it.contains("IF_STATEMENT") })
        assertTrue(types.any { it.contains("FOREACH_STATEMENT") })
    }

    /** A header with nothing under it must not offer a fold arrow. */
    fun testEmptyBlockDoesNotFold() {
        val folds = foldsIn("= Empty =\n")
        assertEquals("A section with no body must not fold", 0, folds.size)
    }

    fun testSpecLinesDoNotFold() {
        val types = foldsIn("= Main =\n    header:\n        visible\n")
            .map { it.element.elementType.toString() }
        assertFalse("Individual spec lines must not fold", types.any { it.contains("SPEC_LINE") })
    }

    fun testPlaceholderShowsTheHeaderLine() {
        val texts = placeholders(
            """
            = Main section =
                header:
                    visible
            """.trimIndent(),
        )
        assertTrue("Expected the section header as placeholder, got $texts", texts.contains("= Main section = ..."))
        assertTrue("Expected the object header as placeholder, got $texts", texts.contains("header: ..."))
    }

    /**
     * Regression: the fold must cover the header, because the placeholder repeats it.
     *
     * Folding only the body rendered the header twice — `hero-header:hero-header: ...`.
     */
    fun testFoldCoversTheHeaderSoItIsNotDuplicated() {
        val text = "= Main =\n    hero-header:\n        visible\n        width 100px\n"
        val folds = foldsIn(text)

        val statement = folds.first { it.element.elementType.toString().contains("OBJECT_STATEMENT") }
        val folded = text.substring(statement.range.startOffset, statement.range.endOffset)
        assertTrue(
            "The folded region must include the header text, got '$folded'",
            folded.startsWith("hero-header:"),
        )

        val placeholder = GalenFoldingBuilder().getPlaceholderText(statement.element)
        assertEquals("hero-header: ...", placeholder)
    }

    /** Indentation stays outside the fold, so a collapsed block keeps its place in the structure. */
    fun testFoldStartsAfterTheIndentation() {
        val text = "= Main =\n    hero-header:\n        visible\n"
        val statement = foldsIn(text).first { it.element.elementType.toString().contains("OBJECT_STATEMENT") }
        assertEquals(text.indexOf("hero-header"), statement.range.startOffset)
    }

    fun testTopLevelFoldStartsAtColumnZero() {
        val text = "= Main =\n    header:\n        visible\n"
        val section = foldsIn(text).first { it.element.elementType.toString().contains("SECTION") }
        assertEquals(0, section.range.startOffset)
        assertFalse(
            "Fold must not include the trailing newline",
            text.substring(section.range.startOffset, section.range.endOffset).endsWith("\n"),
        )
    }
}
