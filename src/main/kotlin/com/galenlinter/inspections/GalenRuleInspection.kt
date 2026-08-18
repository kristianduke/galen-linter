package com.galenlinter.inspections

import com.galenlinter.lang.GalenTypes
import com.galenlinter.psi.GalenFile
import com.galenlinter.resolve.GalenVariableUtil
import com.galenlinter.rules.GalenRule
import com.galenlinter.rules.GalenRuleUtil
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil

/**
 * GL6xx — custom rules.
 *
 * Rules are how a team factors its specs, and they are entirely unchecked by anything else: an
 * invocation that matches no rule simply does nothing, and a rule text and its call site can drift
 * apart with no signal at all.
 */
class GalenRuleInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element.containingFile !is GalenFile) return
                when (element.node?.elementType) {
                    GalenTypes.RULE_INVOCATION -> checkInvocation(element, holder)
                    GalenTypes.RULE_DEFINITION -> checkDefinition(element, holder)
                    GalenTypes.RULE_BODY_STATEMENT -> checkRuleBody(element, holder)
                }
            }
        }

    // ---- invocations -------------------------------------------------------

    private fun checkInvocation(invocation: PsiElement, holder: ProblemsHolder) {
        val text = GalenRuleUtil.invocationTextOf(invocation) ?: return
        // An invocation built from an expression cannot be matched until run time.
        if (text.contains("\${")) return

        val rules = GalenRuleUtil.rulesInScope(invocation.containingFile)
        val matching = rules.filter { it.matches(text) }

        when {
            matching.isEmpty() -> reportUnmatched(invocation, text, rules, holder)
            matching.size > 1 -> reportAmbiguous(invocation, text, matching, holder)
            else -> checkBody(invocation, matching.single(), holder)
        }
    }

    private fun reportUnmatched(
        invocation: PsiElement,
        text: String,
        rules: List<GalenRule>,
        holder: ProblemsHolder,
    ) {
        val closest = rules
            .filter { it.pattern != null }
            .minByOrNull { distance(text, it.text) }

        val hint = if (closest != null && distance(text, closest.text) <= text.length / 2) {
            " The closest declared rule is '${closest.text}'."
        } else {
            ""
        }

        holder.registerProblem(
            invocation,
            headerRangeOf(invocation),
            "GL602: No rule matches '$text', so this line does nothing.$hint",
        )
    }

    /**
     * Two rules matching one invocation is genuinely dangerous: Galen picks one, and which one is
     * not something the spec author can see. Default parameter matching is `.*`, which makes this
     * easy to cause by accident.
     */
    private fun reportAmbiguous(
        invocation: PsiElement,
        text: String,
        matching: List<GalenRule>,
        holder: ProblemsHolder,
    ) {
        val names = matching.joinToString("; ") { "'${it.text}' (${it.sourceName})" }
        holder.registerProblem(
            invocation,
            headerRangeOf(invocation),
            "GL601: '$text' matches ${matching.size} rules, so which one runs is unpredictable: $names",
        )
    }

    /** A block under an invocation only runs if the rule invokes `@ruleBody`. */
    private fun checkBody(invocation: PsiElement, rule: GalenRule, holder: ProblemsHolder) {
        if (!GalenRuleUtil.hasBody(invocation)) return
        if (rule.invokesBody) return

        holder.registerProblem(
            invocation,
            headerRangeOf(invocation),
            "GL604: Rule '${rule.text}' never invokes '@ruleBody', so the block below this " +
                "invocation is silently ignored.",
        )
    }

    // ---- definitions -------------------------------------------------------

    private fun checkDefinition(rule: PsiElement, holder: ProblemsHolder) {
        val text = GalenRuleUtil.ruleTextOf(rule) ?: return
        val parameters = GalenRuleUtil.parametersOf(text)

        // GL606 — a custom capture regex that does not compile.
        for (parameter in parameters) {
            if (GalenRuleUtil.isValidRegex(parameter.regex)) continue
            holder.registerProblem(
                rule,
                headerRangeOf(rule),
                "GL606: '${parameter.regex}' is not a valid regular expression, so the parameter " +
                    "'${parameter.name}' can never match.",
            )
        }

        checkBodyParameters(rule, parameters.map { it.name }, holder)
    }

    /**
     * GL607 — the body uses a `${...}` the rule does not declare.
     *
     * Only reported when the name resolves to nothing at all: a rule body may legitimately use
     * `objectName` (supplied automatically on an object-scoped rule), a `@set` variable, an
     * enclosing loop binding, or Galen's JavaScript API.
     */
    private fun checkBodyParameters(
        rule: PsiElement,
        declared: List<String>,
        holder: ProblemsHolder,
    ) {
        val headerEnd = rule.node?.findChildByType(GalenTypes.EOL)?.startOffset ?: return

        for (element in PsiTreeUtil.findChildrenOfAnyType(rule, PsiElement::class.java)) {
            if (element.node?.elementType != GalenTypes.EXPRESSION) continue
            if (element.textRange.startOffset < headerEnd) continue

            val range = GalenVariableUtil.identifierRangeIn(element.text) ?: continue
            val name = range.substring(element.text)

            if (name in declared) continue
            if (name in AUTOMATIC) continue
            // A call is not a parameter reference. This covers Galen's own API (`isVisible`,
            // `count`, `find`) and anything a @script file defines, without needing to know them.
            if (isCall(element.text, range.endOffset)) continue
            if (GalenVariableUtil.findDeclaration(name, element) != null) continue

            val hint = declared.minByOrNull { distance(name, it) }
                ?.takeIf { distance(name, it) <= 2 }
                ?.let { " Did you mean '$it'?" }
                ?: " Declared parameters: ${declared.joinToString(", ").ifEmpty { "none" }}."

            holder.registerProblem(
                element,
                TextRange(range.startOffset, range.endOffset),
                "GL607: '$name' is not a parameter of this rule.$hint",
            )
        }
    }

    /** GL603 — `@ruleBody` only means anything inside a rule. */
    private fun checkRuleBody(element: PsiElement, holder: ProblemsHolder) {
        var parent: PsiElement? = element.parent
        while (parent != null && parent !is GalenFile) {
            if (parent.node?.elementType == GalenTypes.RULE_DEFINITION) return
            parent = parent.parent
        }
        holder.registerProblem(
            element,
            headerRangeOf(element),
            "GL603: '@ruleBody' is only meaningful inside a '@rule' declaration.",
        )
    }

    // ---- helpers -----------------------------------------------------------

    /** True when the identifier ending at [after] is immediately applied, i.e. it is a function. */
    private fun isCall(text: String, after: Int): Boolean {
        var index = after
        while (index < text.length && text[index].isWhitespace()) index++
        return index < text.length && text[index] == '('
    }

    /** The header line, so the squiggle does not cover the whole indented block. */
    private fun headerRangeOf(element: PsiElement): TextRange {
        val start = element.textRange.startOffset
        val eol = element.node?.findChildByType(GalenTypes.EOL)?.startOffset
        val end = eol ?: element.textRange.endOffset
        return TextRange(0, (end - start).coerceAtLeast(1))
    }

    private fun distance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, substitution)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    private companion object {
        /** Supplied by Galen rather than declared: the object an object-scoped rule was applied to. */
        val AUTOMATIC = setOf("objectName")
    }
}
