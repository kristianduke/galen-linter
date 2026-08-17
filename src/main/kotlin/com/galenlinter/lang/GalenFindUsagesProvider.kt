package com.galenlinter.lang

import com.galenlinter.lexer.GalenLexer
import com.galenlinter.psi.GalenObjectDefinition
import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet

/**
 * Enables Find Usages on object declarations.
 *
 * The words scanner is what lets the platform pre-filter candidate files by identifier before
 * resolving anything, which is what keeps Find Usages from parsing the whole project.
 */
class GalenFindUsagesProvider : FindUsagesProvider {

    override fun getWordsScanner(): WordsScanner = DefaultWordsScanner(
        GalenLexer(),
        TokenSet.create(GalenTypes.WORD),
        GalenTypes.COMMENTS,
        GalenTypes.STRINGS,
    )

    override fun canFindUsagesFor(element: PsiElement): Boolean = element is GalenObjectDefinition

    override fun getHelpId(element: PsiElement): String? = null

    override fun getType(element: PsiElement): String =
        if (element is GalenObjectDefinition && element.isPattern) "object family" else "object"

    override fun getDescriptiveName(element: PsiElement): String =
        (element as? GalenObjectDefinition)?.qualifiedName ?: ""

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String {
        val definition = element as? GalenObjectDefinition ?: return ""
        val name = definition.qualifiedName ?: return ""
        val locator = definition.locatorText()
        return if (locator != null) "$name (${definition.locatorType()} $locator)" else name
    }
}
