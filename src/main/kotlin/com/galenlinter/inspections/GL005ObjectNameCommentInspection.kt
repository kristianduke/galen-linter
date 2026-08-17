package com.galenlinter.inspections

import com.galenlinter.psi.GalenFile
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.util.TextRange

/**
 * GL005 — an object definition whose name begins with `#` is silently swallowed as a comment.
 *
 * Galen decides a line is a comment by testing whether its *trimmed* text starts with `#`
 * (`IndentationStructureParser`), and it does that **before** any structural parsing. So inside an
 * `@objects` block a line such as
 *
 * ```
 *     #footer   div.footer
 * ```
 *
 * defines nothing at all, and every later reference to that object fails at run time with no
 * syntax error to point at. This is the language's sharpest silent-failure trap.
 *
 * ### Heuristic
 * Prose comments are common and legitimate inside `@objects`, so this only fires when the line
 * looks structurally like a definition:
 *  - no space between `#` and the first word (`#footer x` fires, `# footer x` does not), and
 *  - at least two whitespace-separated fields (so `# containers` never fires).
 *
 * Reported as a weak warning because the heuristic cannot be exact.
 */
class GL005ObjectNameCommentInspection : GalenFileInspection() {

    override fun analyze(file: GalenFile, report: Reporter) {
        val lines = linesOf(file.text)

        var objectsBlockIndent: Int? = null

        for (line in lines) {
            if (line.isBlank) continue

            val trimmed = line.text.trim()

            // Track whether we are inside the body of an @objects block.
            val blockIndent = objectsBlockIndent
            if (blockIndent != null && !line.isComment && line.indentWidth <= blockIndent) {
                objectsBlockIndent = null
            }
            if (trimmed.startsWith("@objects")) {
                objectsBlockIndent = line.indentWidth
                continue
            }
            if (objectsBlockIndent == null) continue
            if (!line.isComment) continue

            if (!LOOKS_LIKE_DEFINITION.matches(trimmed)) continue

            val offset = line.start + line.indent.length
            report.problem(
                TextRange(offset, line.end),
                "GL005: This line is parsed as a comment, not an object definition. " +
                    "Galen treats any line whose first non-blank character is '#' as a comment, " +
                    "so this object is never defined. Rename it or use an explicit locator type.",
                ProblemHighlightType.WEAK_WARNING,
            )
        }
    }

    private companion object {
        /** `#name` with no space after `#`, followed by at least one more field. */
        val LOOKS_LIKE_DEFINITION = Regex("""^#\S+\s+\S.*$""")
    }
}
