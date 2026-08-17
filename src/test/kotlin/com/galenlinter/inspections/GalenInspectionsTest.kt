package com.galenlinter.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class GalenInspectionsTest : BasePlatformTestCase() {

    private fun findingsFor(tool: LocalInspectionTool, text: String, ruleId: String): List<String> {
        myFixture.enableInspections(tool)
        myFixture.configureByText("test.gspec", text)
        return myFixture.doHighlighting()
            .mapNotNull { it.description }
            .filter { it.contains(ruleId) }
    }

    // ---- GL005 ------------------------------------------------------------

    fun testGL005FlagsObjectNameSwallowedAsComment() {
        val findings = findingsFor(
            GL005ObjectNameCommentInspection(),
            """
            @objects
                #footer   div.footer
                header    div.header
            """.trimIndent(),
            "GL005",
        )
        assertEquals(1, findings.size)
    }

    fun testGL005IgnoresProseComments() {
        val findings = findingsFor(
            GL005ObjectNameCommentInspection(),
            """
            @objects
                # the main containers
                header    div.header
            """.trimIndent(),
            "GL005",
        )
        assertTrue("Prose comments must not be flagged: $findings", findings.isEmpty())
    }

    fun testGL005IgnoresCommentsOutsideObjectsBlock() {
        val findings = findingsFor(
            GL005ObjectNameCommentInspection(),
            """
            #footer   div.footer

            = Main =
                header:
                    visible
            """.trimIndent(),
            "GL005",
        )
        assertTrue("Only @objects bodies are relevant: $findings", findings.isEmpty())
    }

    // ---- GL001 ------------------------------------------------------------

    fun testGL001FlagsTabAndSpaceInSameIndent() {
        val findings = findingsFor(
            GL001MixedIndentationInspection(),
            "= Main =\n \theader:\n\t\tvisible\n",
            "GL001",
        )
        assertTrue("Expected a mixed-indentation finding", findings.isNotEmpty())
    }

    fun testGL001AcceptsConsistentSpaces() {
        val findings = findingsFor(
            GL001MixedIndentationInspection(),
            "= Main =\n    header:\n        visible\n",
            "GL001",
        )
        assertTrue(findings.isEmpty())
    }

    // ---- GL003 / GL006 ----------------------------------------------------

    fun testGL003FlagsTrailingWhitespace() {
        val findings = findingsFor(
            GL003TrailingWhitespaceInspection(),
            "= Main =   \n    header:\n        visible\n",
            "GL003",
        )
        assertEquals(1, findings.size)
    }

    fun testGL006FlagsMissingFinalNewline() {
        val findings = findingsFor(
            GL006MissingFinalNewlineInspection(),
            "= Main =\n    header:\n        visible",
            "GL006",
        )
        assertEquals(1, findings.size)
    }

    fun testGL006AcceptsTrailingNewline() {
        val findings = findingsFor(
            GL006MissingFinalNewlineInspection(),
            "= Main =\n    header:\n        visible\n",
            "GL006",
        )
        assertTrue(findings.isEmpty())
    }
}
