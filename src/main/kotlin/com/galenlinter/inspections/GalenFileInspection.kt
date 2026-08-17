package com.galenlinter.inspections

import com.galenlinter.psi.GalenFile
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

/** One physical line of a spec file, with absolute offsets. */
data class SpecLine(val index: Int, val start: Int, val text: String) {
    val end: Int get() = start + text.length

    /** Leading horizontal whitespace. */
    val indent: String get() = text.takeWhile { it == ' ' || it == '\t' }

    val isBlank: Boolean get() = text.isBlank()

    val isComment: Boolean get() = text.trimStart().startsWith("#")

    /** Indentation depth in columns, using Galen's `TAB_SIZE` of 4. */
    val indentWidth: Int get() = indent.fold(0) { acc, c -> acc + if (c == '\t') 4 else 1 }
}

/**
 * Base for whole-file inspections.
 *
 * The GL0xx rules are all about raw text (indentation, trailing space, final newline), so they
 * work off the file's character content rather than the PSI tree.
 */
abstract class GalenFileInspection : LocalInspectionTool() {

    final override fun checkFile(
        file: PsiFile,
        manager: InspectionManager,
        isOnTheFly: Boolean,
    ): Array<ProblemDescriptor>? {
        if (file !is GalenFile) return null
        val problems = mutableListOf<ProblemDescriptor>()
        analyze(file, Reporter(file, manager, isOnTheFly, problems))
        return if (problems.isEmpty()) null else problems.toTypedArray()
    }

    protected abstract fun analyze(file: GalenFile, report: Reporter)

    protected class Reporter(
        private val file: PsiFile,
        private val manager: InspectionManager,
        private val onTheFly: Boolean,
        private val sink: MutableList<ProblemDescriptor>,
    ) {
        fun problem(
            range: TextRange,
            message: String,
            type: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            vararg fixes: LocalQuickFix,
        ) {
            if (range.startOffset >= range.endOffset) return
            sink += manager.createProblemDescriptor(file, range, message, type, onTheFly, *fixes)
        }
    }

    companion object {
        /** Splits file text into lines, preserving absolute offsets. */
        fun linesOf(text: CharSequence): List<SpecLine> {
            val result = mutableListOf<SpecLine>()
            var start = 0
            var index = 0
            var i = 0
            while (i <= text.length) {
                if (i == text.length || text[i] == '\n') {
                    var lineEnd = i
                    if (lineEnd > start && text[lineEnd - 1] == '\r') lineEnd--
                    result += SpecLine(index++, start, text.substring(start, lineEnd))
                    start = i + 1
                    if (i == text.length) break
                }
                i++
            }
            return result
        }
    }
}
