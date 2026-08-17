package com.galenlinter.inspections

import com.galenlinter.lang.GalenTypes
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

/** Small PSI navigation helpers shared by the spec inspections. */
internal object SpecPsiUtil {

    fun typeOf(element: PsiElement): IElementType? = element.node?.elementType

    /** Direct children of the given type. */
    fun childrenOfType(element: PsiElement, type: IElementType): List<PsiElement> =
        element.node?.getChildren(TokenSet.create(type))?.mapNotNull { it.psi } ?: emptyList()

    fun firstChildOfType(element: PsiElement, type: IElementType): PsiElement? =
        element.node?.findChildByType(type)?.psi

    /** All descendants of the given type, in document order. */
    fun descendantsOfType(element: PsiElement, type: IElementType): List<PsiElement> {
        val result = mutableListOf<PsiElement>()
        fun walk(current: PsiElement) {
            if (typeOf(current) == type) result += current
            current.children.forEach(::walk)
        }
        element.children.forEach(::walk)
        return result
    }

    /** The spec name of a SPEC_LINE, or null if the line has none. */
    fun specNameOf(specLine: PsiElement): String? =
        firstChildOfType(specLine, GalenTypes.SPEC_NAME)?.text

    fun argsOf(specLine: PsiElement): PsiElement? =
        firstChildOfType(specLine, GalenTypes.SPEC_ARGS)

    /**
     * True when the text is, or contains, a `${...}` expression.
     *
     * Every validation must skip these. A spec argument supplied by an expression is only knowable
     * at run time, and treating an unknown as a mistake is how a linter becomes something people
     * switch off.
     */
    fun isDynamic(text: String): Boolean = text.contains("\${")

    /**
     * The nearest known word to [word] within an edit distance of 2, for "did you mean".
     * Returns null when nothing is close enough to be worth suggesting.
     */
    fun closestMatch(word: String, candidates: Collection<String>): String? {
        if (word.isEmpty()) return null
        var best: String? = null
        var bestDistance = Int.MAX_VALUE
        for (candidate in candidates) {
            val distance = editDistance(word.lowercase(), candidate.lowercase())
            if (distance < bestDistance) {
                bestDistance = distance
                best = candidate
            }
        }
        val limit = if (word.length <= 4) 1 else 2
        return if (bestDistance <= limit) best else null
    }

    private fun editDistance(a: String, b: String): Int {
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
}
