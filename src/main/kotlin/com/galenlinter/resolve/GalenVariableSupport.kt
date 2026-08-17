package com.galenlinter.resolve

import com.galenlinter.lang.GalenTypes
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException

/**
 * Navigation for `${...}` variables.
 *
 * Deliberately narrow: only the **leading identifier** of a simple expression is treated as a
 * reference — `${gutter}`, `${item.name}`, `${data[i]}`. Parsing arbitrary JavaScript to find every
 * identifier would drag a whole language implementation in for very little benefit, and would
 * mostly turn up Galen's own API (`find`, `count`, `isVisible`) rather than user variables.
 *
 * There is no accompanying inspection. An expression can legitimately reference Galen's JS API, a
 * function loaded by `@script`, or an argument passed in by whichever spec uses this file as a
 * component — so "unknown variable" cannot be decided locally, and guessing would produce noise.
 */
object GalenVariableUtil {

    /**
     * Galen validates variable names as `[a-zA-Z_][a-zA-Z0-9_]*`
     * (`PageSpecHandler.isValidVariableName`) — narrower than object names, which allow `-` and `.`.
     */
    private val LEADING_IDENTIFIER = Regex("""^\$\{\s*([A-Za-z_][A-Za-z0-9_]*)""")

    /** `as <name>` in a `@for` / `@forEach` header, including the `next`/`prev`/`index` bindings. */
    private val LOOP_BINDING = Regex("""\bas\s+([A-Za-z_][A-Za-z0-9_]*)""")

    /** `%{name}` or `%{name: regex}` in a rule definition. */
    private val RULE_PARAMETER = Regex("""^%\{\s*([A-Za-z_][A-Za-z0-9_]*)""")

    /** The identifier range inside an `${...}` token, relative to the token's start. */
    fun identifierRangeIn(text: String): TextRange? {
        val match = LEADING_IDENTIFIER.find(text) ?: return null
        val group = match.groups[1] ?: return null
        return TextRange(group.range.first, group.range.last + 1)
    }

    /**
     * The declaration of [name] visible from [from].
     *
     * Scope follows the PSI: a loop or rule binding is only visible inside that construct, while
     * `@set` is file-level.
     */
    fun findDeclaration(name: String, from: PsiElement): PsiElement? {
        var ancestor: PsiElement? = from
        while (ancestor != null && ancestor !is com.intellij.psi.PsiFile) {
            when (ancestor.node?.elementType) {
                GalenTypes.FOR_STATEMENT, GalenTypes.FOREACH_STATEMENT ->
                    if (loopBindingsOf(ancestor).contains(name)) return ancestor

                GalenTypes.RULE_DEFINITION ->
                    ruleParameterOf(ancestor, name)?.let { return it }
            }
            ancestor = ancestor.parent
        }

        return setEntryOf(from.containingFile, name)
    }

    fun namesInScope(from: PsiElement): List<String> {
        val names = mutableListOf<String>()

        var ancestor: PsiElement? = from
        while (ancestor != null && ancestor !is com.intellij.psi.PsiFile) {
            when (ancestor.node?.elementType) {
                GalenTypes.FOR_STATEMENT, GalenTypes.FOREACH_STATEMENT ->
                    names += loopBindingsOf(ancestor)

                GalenTypes.RULE_DEFINITION ->
                    names += ruleParametersOf(ancestor)
            }
            ancestor = ancestor.parent
        }

        names += setEntryNames(from.containingFile)
        return names.distinct()
    }

    /** Only the loop's own header line, not the bindings of any nested loop inside its body. */
    private fun loopBindingsOf(loop: PsiElement): List<String> {
        val header = headerLineOf(loop)
        return LOOP_BINDING.findAll(header).mapNotNull { it.groups[1]?.value }.toList()
    }

    private fun ruleParametersOf(rule: PsiElement): List<String> =
        ruleParameterTokens(rule).mapNotNull { token ->
            RULE_PARAMETER.find(token.text)?.groups?.get(1)?.value
        }

    private fun ruleParameterOf(rule: PsiElement, name: String): PsiElement? =
        ruleParameterTokens(rule).firstOrNull { token ->
            RULE_PARAMETER.find(token.text)?.groups?.get(1)?.value == name
        }

    /** `%{...}` tokens on the rule's own header line. */
    private fun ruleParameterTokens(rule: PsiElement): List<PsiElement> {
        val headerEnd = rule.node.findChildByType(GalenTypes.EOL)?.startOffset ?: rule.textRange.endOffset
        return PsiTreeUtil.findChildrenOfAnyType(rule, PsiElement::class.java)
            .filter { it.node?.elementType == GalenTypes.RULE_PARAM }
            .filter { it.textRange.endOffset <= headerEnd }
    }

    private fun headerLineOf(element: PsiElement): String {
        val end = element.node.findChildByType(GalenTypes.EOL)?.startOffset
            ?: element.textRange.endOffset
        val start = element.textRange.startOffset
        return element.containingFile.text.substring(start, end)
    }

    private fun setEntries(file: PsiElement): List<PsiElement> =
        PsiTreeUtil.findChildrenOfAnyType(file, PsiElement::class.java)
            .filter { it.node?.elementType == GalenTypes.SET_ENTRY }

    private fun setEntryNames(file: PsiElement): List<String> =
        setEntries(file).mapNotNull { nameOfSetEntry(it) }

    private fun setEntryOf(file: PsiElement, name: String): PsiElement? =
        setEntries(file).firstOrNull { nameOfSetEntry(it) == name }

    fun nameOfSetEntry(entry: PsiElement): String? =
        entry.text.trim().takeWhile { !it.isWhitespace() }.takeIf { it.isNotEmpty() }
}

class GalenVariableReference(element: PsiElement, range: TextRange) :
    PsiReferenceBase<PsiElement>(element, range, true) {

    override fun resolve(): PsiElement? =
        GalenVariableUtil.findDeclaration(value, element)

    override fun getVariants(): Array<Any> {
        val names: List<Any> = GalenVariableUtil.namesInScope(element)
        return names.toTypedArray()
    }

    override fun handleElementRename(newElementName: String): PsiElement =
        // The identifier lives inside an opaque expression token that may contain arbitrary
        // JavaScript. Rewriting part of it safely needs a JS parser, so refuse clearly rather than
        // corrupt the expression.
        throw IncorrectOperationException(
            "Renaming a Galen variable is not supported: '${element.text}' is a JavaScript " +
                "expression, so its occurrences cannot be rewritten reliably.",
        )
}
