package com.galenlinter.inspections

import com.galenlinter.psi.GalenFile
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.util.TextRange

/**
 * GL001 — indentation mixes tabs and spaces.
 *
 * Legal in Galen (a tab is worth exactly 4 columns), but a reader whose editor renders tabs at a
 * different width will compute different block structure than Galen does, so this silently
 * misleads.
 */
class GL001MixedIndentationInspection : GalenFileInspection() {
    override fun analyze(file: GalenFile, report: Reporter) {
        val lines = linesOf(file.text).filterNot { it.isBlank }

        for (line in lines) {
            val indent = line.indent
            if (indent.contains('\t') && indent.contains(' ')) {
                report.problem(
                    TextRange(line.start, line.start + indent.length),
                    "GL001: Indentation mixes tabs and spaces. Galen counts a tab as 4 columns; " +
                        "editors may render it differently.",
                )
            }
        }

        // Also flag a file that is internally inconsistent: some lines tab-indented, others not.
        val tabIndented = lines.count { it.indent.startsWith("\t") }
        val spaceIndented = lines.count { it.indent.startsWith(" ") }
        if (tabIndented > 0 && spaceIndented > 0) {
            val first = lines.first { it.indent.isNotEmpty() }
            report.problem(
                TextRange(first.start, first.start + first.indent.length),
                "GL001: This file indents some lines with tabs and others with spaces.",
                ProblemHighlightType.WEAK_WARNING,
            )
        }
    }
}

/**
 * GL002 — the indentation step is not consistent within the file.
 *
 * Galen only requires that siblings agree, so a file may legally step by 4 in one block and 2 in
 * another. That is a readability hazard rather than an error.
 */
class GL002InconsistentIndentStepInspection : GalenFileInspection() {
    override fun analyze(file: GalenFile, report: Reporter) {
        val lines = linesOf(file.text).filterNot { it.isBlank || it.isComment }
        val steps = mutableMapOf<Int, SpecLine>()

        var previousWidth = 0
        for (line in lines) {
            val width = line.indentWidth
            if (width > previousWidth) {
                val step = width - previousWidth
                steps.putIfAbsent(step, line)
            }
            previousWidth = width
        }

        if (steps.size > 1) {
            val sorted = steps.keys.sorted()
            // Report on every step size other than the most common (first-seen) one.
            val dominant = sorted.first()
            for ((step, line) in steps) {
                if (step == dominant) continue
                report.problem(
                    TextRange(line.start, line.start + line.indent.length),
                    "GL002: Indent step of $step column(s) is inconsistent with $dominant " +
                        "used elsewhere in this file.",
                    ProblemHighlightType.WEAK_WARNING,
                )
            }
        }
    }
}

/** GL003 — trailing whitespace. */
class GL003TrailingWhitespaceInspection : GalenFileInspection() {
    override fun analyze(file: GalenFile, report: Reporter) {
        for (line in linesOf(file.text)) {
            if (line.text.isEmpty()) continue
            val trimmedLength = line.text.trimEnd().length
            if (trimmedLength == line.text.length) continue
            report.problem(
                TextRange(line.start + trimmedLength, line.end),
                "GL003: Trailing whitespace.",
                ProblemHighlightType.WEAK_WARNING,
            )
        }
    }
}

/** GL006 — the file does not end with a newline. */
class GL006MissingFinalNewlineInspection : GalenFileInspection() {
    override fun analyze(file: GalenFile, report: Reporter) {
        val text = file.text
        if (text.isEmpty() || text.endsWith("\n")) return
        report.problem(
            TextRange(maxOf(0, text.length - 1), text.length),
            "GL006: File does not end with a newline.",
            ProblemHighlightType.WEAK_WARNING,
        )
    }
}
