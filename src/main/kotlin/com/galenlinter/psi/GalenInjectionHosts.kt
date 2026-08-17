package com.galenlinter.psi

import com.galenlinter.lang.GalenTypes
import com.intellij.lang.ASTFactory
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType

/**
 * A `${...}` expression, as a language injection host.
 *
 * Injection requires the host to implement [PsiLanguageInjectionHost], and an expression is a
 * single lexer token — so the PSI class of that *leaf* has to be ours. Leaves do not come from
 * `ParserDefinition.createElement`, which only builds composites, so [GalenAstFactory] supplies it.
 */
class GalenExpressionElement(type: IElementType, text: CharSequence) :
    LeafPsiElement(type, text), PsiLanguageInjectionHost {

    override fun isValidHost(): Boolean = text.startsWith("\${") && text.endsWith("}")

    override fun updateText(text: String): PsiLanguageInjectionHost {
        val replaced = (this as LeafElement).replaceWithText(text)
        return replaced as? PsiLanguageInjectionHost ?: this
    }

    override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> =
        LiteralTextEscaper.createSimple(this)

    /** The JavaScript inside the `${` and `}` delimiters. */
    fun expressionRange(): TextRange? {
        if (!isValidHost) return null
        val inner = TextRange(2, textLength - 1)
        return if (inner.isEmpty) null else inner
    }
}

/** One raw JavaScript line inside a `@script` block. */
class GalenRawJsLine(node: ASTNode) : GalenPsiElement(node), PsiLanguageInjectionHost {

    override fun isValidHost(): Boolean = contentRange() != null

    override fun updateText(text: String): PsiLanguageInjectionHost = this

    override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> =
        LiteralTextEscaper.createSimple(this)

    /** The line's text with its leading indentation excluded. */
    fun contentRange(): TextRange? {
        val indent = node.firstChildNode?.takeIf { it.elementType == GalenTypes.LINE_INDENT }
        val start = if (indent != null) indent.textLength else 0
        var end = textLength
        val text = text
        while (end > start && (text[end - 1] == '\n' || text[end - 1] == '\r')) end--
        return if (end > start) TextRange(start, end) else null
    }
}

/**
 * Supplies the PSI class for leaf tokens.
 *
 * Only the expression token needs one; everything else keeps the platform default.
 */
class GalenAstFactory : ASTFactory() {
    override fun createLeaf(type: IElementType, text: CharSequence): LeafElement? =
        if (type == GalenTypes.EXPRESSION) GalenExpressionElement(type, text) else null
}

/** Convenience for the injector and for tests. */
fun PsiElement.asExpressionHost(): GalenExpressionElement? = this as? GalenExpressionElement
