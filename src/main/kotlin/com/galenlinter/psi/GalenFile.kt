package com.galenlinter.psi

import com.galenlinter.lang.GalenFileType
import com.galenlinter.lang.GalenLanguage
import com.galenlinter.lang.GalenTypes
import com.galenlinter.resolve.GalenVariableReference
import com.galenlinter.resolve.GalenVariableUtil
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiReference

class GalenFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, GalenLanguage) {
    override fun getFileType(): FileType = GalenFileType

    override fun toString(): String = "Galen Spec File"
}

/**
 * Generic PSI wrapper for every composite node that needs no special behaviour.
 *
 * It does carry one responsibility: exposing `${...}` variable references.
 *
 * They cannot be attached to the expression token itself, because a `LeafPsiElement` has no
 * references, and they cannot come from a `PsiReferenceContributor` either — contributed references
 * are only consulted for elements implementing `ContributedReferenceHost`. Building them here on
 * the enclosing composite is both reliable and less machinery. Only *direct* children are
 * considered, so nesting cannot produce duplicate references for one token.
 */
open class GalenPsiElement(node: ASTNode) : ASTWrapperPsiElement(node) {

    override fun getReferences(): Array<PsiReference> {
        val elementStart = textRange.startOffset
        val references = mutableListOf<PsiReference>()

        var child = node.firstChildNode
        while (child != null) {
            if (child.elementType == GalenTypes.EXPRESSION) {
                val inner = GalenVariableUtil.identifierRangeIn(child.text)
                if (inner != null) {
                    val offset = child.textRange.startOffset - elementStart
                    references += GalenVariableReference(this, inner.shiftRight(offset))
                }
            }
            child = child.treeNext
        }

        return if (references.isEmpty()) PsiReference.EMPTY_ARRAY else references.toTypedArray()
    }

    override fun getReference(): PsiReference? = references.firstOrNull()
}
