package com.galenlinter.resolve

import com.galenlinter.lang.GalenTypes
import com.galenlinter.psi.GalenFilePathRef
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

object GalenImportUtil {

    /**
     * The paths named by this file's `@import` statements.
     *
     * `@lib` is excluded on purpose: it names a library bundled inside the Galen jar
     * (`/spec-libs/<name>/<name>.gspec`), not a path relative to this file, so it can never be
     * followed on disk.
     */
    fun importPathsOf(file: PsiFile): List<String> =
        importStatementsOf(file).mapNotNull { pathOf(it) }

    fun importStatementsOf(file: PsiFile): List<PsiElement> =
        PsiTreeUtil.findChildrenOfAnyType(file, PsiElement::class.java)
            .filter { it.node?.elementType == GalenTypes.IMPORT_STATEMENT }

    fun pathOf(statement: PsiElement): String? =
        PsiTreeUtil.findChildOfType(statement, GalenFilePathRef::class.java)?.text?.trim()
            ?.takeIf { it.isNotEmpty() }
}
