package com.galenlinter.psi

import com.galenlinter.lang.GalenFileType
import com.galenlinter.lang.GalenLanguage
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider

class GalenFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, GalenLanguage) {
    override fun getFileType(): FileType = GalenFileType

    override fun toString(): String = "Galen Spec File"
}

/**
 * Generic PSI wrapper for every composite node.
 *
 * M1 only needs a well-shaped tree. M2 introduces specialised subclasses implementing
 * `PsiNamedElement` / `PsiReference` for object definitions and references, which is what
 * gives go-to-definition, find-usages and rename.
 */
class GalenPsiElement(node: ASTNode) : ASTWrapperPsiElement(node)
