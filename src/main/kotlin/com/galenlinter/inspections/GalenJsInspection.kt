package com.galenlinter.inspections

import com.galenlinter.js.GalenJsLexer
import com.galenlinter.psi.GalenExpressionElement
import com.galenlinter.psi.GalenFile
import com.galenlinter.psi.GalenRawJsLine
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor

/**
 * GL7xx — checks on embedded JavaScript, when no JavaScript plugin is installed to do it properly.
 *
 * Scope is deliberately small. Without a parser or scope analysis, the only things worth reporting
 * are those that are unambiguous from the token stream: a bracket that never closes, an
 * unterminated string, and two Galen-specific traps. Anything needing to know what a name refers to
 * is left alone — a `@script` file can define anything, and a false positive on working JavaScript
 * would be worse than silence.
 */
class GalenJsInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element.containingFile !is GalenFile) return
                // A real JavaScript plugin does this far better; do not compete with it.
                if (!GalenJsLexer.isFallbackNeeded()) return

                val range = when (element) {
                    is GalenExpressionElement -> element.expressionRange()
                    is GalenRawJsLine -> element.contentRange()
                    else -> null
                } ?: return

                // A @script body is one program spread over lines, so an unclosed brace on the
                // first line is closed on a later one. Bracket balance is only meaningful for a
                // self-contained ${...} expression.
                val isSelfContained = element is GalenExpressionElement

                val text = range.substring(element.text)
                for (problem in GalenJsLexer.problems(text)) {
                    if (!isSelfContained && problem is GalenJsLexer.Problem.UnbalancedBracket) continue

                    holder.registerProblem(
                        element,
                        "${ruleIdOf(problem)}: ${problem.message}",
                        severityOf(problem),
                        TextRange(
                            range.startOffset + problem.range.startOffset,
                            range.startOffset + problem.range.endOffset,
                        ),
                    )
                }
            }
        }

    private fun ruleIdOf(problem: GalenJsLexer.Problem): String = when (problem) {
        is GalenJsLexer.Problem.NameCalledAsFunction -> "GL701"
        is GalenJsLexer.Problem.UnterminatedString -> "GL702"
        is GalenJsLexer.Problem.UnbalancedBracket -> "GL704"
        is GalenJsLexer.Problem.UnknownApi -> "GL703"
    }

    private fun severityOf(problem: GalenJsLexer.Problem): ProblemHighlightType = when (problem) {
        // These cannot run: the expression will not evaluate.
        is GalenJsLexer.Problem.UnterminatedString,
        is GalenJsLexer.Problem.UnbalancedBracket,
        -> ProblemHighlightType.GENERIC_ERROR

        else -> ProblemHighlightType.GENERIC_ERROR_OR_WARNING
    }
}
