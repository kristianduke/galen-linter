package com.galenlinter.resolve

import com.galenlinter.lang.GalenFileType
import com.galenlinter.psi.GalenObjectDefinition
import com.galenlinter.psi.GalenObjectNameRef
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.search.FileTypeIndex
import com.intellij.util.Processor

/**
 * Finds the usages of a wildcard object family.
 *
 * The platform's default reference search is text-driven: it looks for occurrences of the
 * declaration's own name and resolves those. That works for `header`, but a family declared as
 * `row-value-*` is never *written* as `row-value-*` — it is used through its concrete members,
 * `row-value-1`, `row-value-2`. Those share no searchable text with the declaration, so Find Usages
 * on a family previously came back empty even though every one of those references resolves to it.
 *
 * Exact-name declarations are left to the default searcher, which is index-backed and faster.
 */
class GalenWildcardReferencesSearcher :
    QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {

    override fun processQuery(
        parameters: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>,
    ) {
        val target = parameters.elementToSearch as? GalenObjectDefinition ?: return
        if (!target.isPattern) return

        val pattern = target.qualifiedName ?: return
        val project = target.project
        val scope = parameters.effectiveSearchScope
        val manager = PsiManager.getInstance(project)

        // Finding the candidate files is index-backed; only Galen files are then parsed. A text
        // search cannot narrow this further, since the usages contain none of the pattern's text.
        val files = FileTypeIndex.getFiles(GalenFileType, GlobalSearchScope.projectScope(project))

        for (file in files) {
            if (!scope.contains(file)) continue
            val psiFile = manager.findFile(file) ?: continue

            for (reference in PsiTreeUtil.findChildrenOfType(psiFile, GalenObjectNameRef::class.java)) {
                val text = reference.text
                // Cheap filter first: only resolve names the pattern could possibly match.
                if (!GalenObjectResolver.matchesPattern(pattern, text)) continue

                val psiReference = reference.reference ?: continue
                // Confirm it really resolves here — another file may declare a closer match.
                if (psiReference.isReferenceTo(target)) consumer.process(psiReference)
            }
        }
    }
}
