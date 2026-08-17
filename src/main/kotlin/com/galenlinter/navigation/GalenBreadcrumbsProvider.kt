package com.galenlinter.navigation

import com.galenlinter.lang.GalenLanguage
import com.galenlinter.lang.GalenTypes
import com.galenlinter.psi.GalenObjectDefinition
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider

/**
 * The breadcrumb strip, showing where the caret sits in a spec's block structure —
 * `= Main section = › hero-header: › width 100px`.
 *
 * Worth having for the same reason as the structure view: the nesting is expressed only by
 * indentation, so once a block runs past a screenful there is nothing on screen that says which
 * section or object the line belongs to.
 */
class GalenBreadcrumbsProvider : BreadcrumbsProvider {

    override fun getLanguages(): Array<Language> = arrayOf(GalenLanguage)

    override fun acceptElement(element: PsiElement): Boolean =
        element.node?.elementType in CRUMBS

    override fun getElementInfo(element: PsiElement): String {
        if (element is GalenObjectDefinition) return element.name ?: "?"

        val text = headerLineOf(element)
        return if (text.length > MAX_LENGTH) text.take(MAX_LENGTH - 1) + "…" else text
    }

    override fun getElementTooltip(element: PsiElement): String? =
        (element as? GalenObjectDefinition)?.let { definition ->
            definition.locatorText()?.let { "${definition.locatorType()} $it" }
        }

    private fun headerLineOf(element: PsiElement): String {
        val end = element.node?.findChildByType(GalenTypes.EOL)?.startOffset
            ?: element.textRange.endOffset
        val start = element.textRange.startOffset
        if (end <= start) return element.text.trim()
        return element.containingFile.text.substring(start, end).trim()
    }

    private companion object {
        const val MAX_LENGTH = 50

        val CRUMBS = setOf(
            GalenTypes.SECTION,
            GalenTypes.OBJECT_STATEMENT,
            GalenTypes.OBJECTS_BLOCK,
            GalenTypes.OBJECT_DEF,
            GalenTypes.RULE_DEFINITION,
            GalenTypes.ON_STATEMENT,
            GalenTypes.IF_STATEMENT,
            GalenTypes.ELSEIF_STATEMENT,
            GalenTypes.ELSE_STATEMENT,
            GalenTypes.FOR_STATEMENT,
            GalenTypes.FOREACH_STATEMENT,
            GalenTypes.SPEC_LINE,
        )
    }
}
