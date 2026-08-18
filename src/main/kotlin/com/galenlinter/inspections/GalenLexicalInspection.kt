package com.galenlinter.inspections

import com.galenlinter.lang.GalenTypes
import com.galenlinter.psi.GalenFile
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor

/**
 * GL106 — a string literal with no closing quote.
 *
 * The lexer deliberately stops an unterminated string at the end of the line rather than swallowing
 * the rest of the file, which keeps the following lines parsing correctly — but it also means
 * nothing else notices. `text is "comprehensive` looks entirely ordinary until the run fails.
 */
class GalenUnterminatedStringInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element.containingFile !is GalenFile) return
                if (element.node?.elementType != GalenTypes.STRING) return
                if (isTerminated(element.text)) return

                holder.registerProblem(
                    element,
                    "GL106: This string has no closing quote.",
                    ProblemHighlightType.GENERIC_ERROR,
                )
            }
        }

    private fun isTerminated(text: String): Boolean {
        if (text.length < 2) return false
        if (text.last() != '"') return false

        // A final quote preceded by an odd number of backslashes is escaped, not a terminator.
        var backslashes = 0
        var index = text.length - 2
        while (index >= 0 && text[index] == '\\') {
            backslashes++
            index--
        }
        return backslashes % 2 == 0
    }
}
