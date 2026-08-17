package com.galenlinter.navigation

import com.galenlinter.index.GalenObjectIndex
import com.galenlinter.psi.GalenObjectDefinition
import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.NavigationItem
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter

/**
 * Go to Symbol (Ctrl+Alt+Shift+N) over Galen objects.
 *
 * Reuses the declaration index, so names are enumerated without opening or parsing anything; only
 * the files behind a chosen name are parsed, to find the element to navigate to.
 */
class GalenGotoSymbolContributor : ChooseByNameContributorEx {

    override fun processNames(
        processor: Processor<in String>,
        scope: GlobalSearchScope,
        filter: IdFilter?,
    ) {
        FileBasedIndex.getInstance().processAllKeys(GalenObjectIndex.NAME, { key ->
            // The sentinel used to enumerate wildcard declarations is not a name anyone can jump to.
            if (key == GalenObjectIndex.PATTERN_KEY) true else processor.process(key)
        }, scope, filter)
    }

    override fun processElementsWithName(
        name: String,
        processor: Processor<in NavigationItem>,
        parameters: FindSymbolParameters,
    ) {
        val project = parameters.project
        val manager = PsiManager.getInstance(project)

        for (file in GalenObjectIndex.filesDeclaring(name, project)) {
            if (!parameters.searchScope.contains(file)) continue
            val psiFile = manager.findFile(file) ?: continue

            for (definition in PsiTreeUtil.findChildrenOfType(psiFile, GalenObjectDefinition::class.java)) {
                if (definition.qualifiedName != name) continue
                if (!processor.process(definition)) return
            }
        }
    }
}
