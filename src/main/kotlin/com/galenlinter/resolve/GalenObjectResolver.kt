package com.galenlinter.resolve

import com.galenlinter.index.GalenObjectIndex
import com.galenlinter.lang.GalenTypes
import com.galenlinter.psi.GalenFile
import com.galenlinter.psi.GalenObjectDefinition
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil

/**
 * The outcome of resolving an object name.
 *
 * The three-way split is the whole point. A great many Galen object names are built at run time —
 * `${item}`, `menu_item-${index}` — and reporting those as undefined would make the inspection
 * useless on any spec that loops. [Dynamic] keeps them silent without pretending they resolved.
 */
sealed interface Resolution {
    /** Found a declaration. */
    data class Found(val declaration: GalenObjectDefinition) : Resolution

    /** A built-in object (`screen`, `viewport`, `parent`, `self`, `global`) — real, but undeclared. */
    object Builtin : Resolution

    /** The name is only knowable at run time; never report it. */
    object Dynamic : Resolution

    /**
     * No declaration in scope. [declaredElsewhere] names a file that does declare it but is not
     * imported, which is nearly always the actual mistake.
     */
    data class NotFound(val declaredElsewhere: String? = null) : Resolution
}

object GalenObjectResolver {

    /** How deep to follow `@import` before giving up; guards against pathological graphs. */
    private const val MAX_IMPORT_DEPTH = 32

    fun resolve(name: String, from: PsiFile): Resolution {
        if (name.isEmpty()) return Resolution.NotFound()
        if (name.contains("\${")) return Resolution.Dynamic
        if (name in GalenTypes.SPECIAL_OBJECTS) return Resolution.Builtin

        // Galen only puts imported objects in scope, so search this file and its transitive
        // imports — not the whole project. Resolving to an unrelated file would invent a
        // relationship the spec does not have.
        val visited = mutableSetOf<VirtualFile>()
        findInScope(name, from, visited, 0)?.let { return Resolution.Found(it) }

        // Not in scope. The index can still say where it *is* declared, which turns a bare
        // "unresolved" into a specific, actionable message about a missing @import.
        val elsewhere = declaredElsewhere(name, from, visited)
        return Resolution.NotFound(elsewhere)
    }

    /** Every declaration reachable from [file] through imports, for completion and diagnostics. */
    fun declarationsInScope(file: PsiFile): List<GalenObjectDefinition> {
        val result = mutableListOf<GalenObjectDefinition>()
        val visited = mutableSetOf<VirtualFile>()
        collect(file, visited, 0, result)
        return result
    }

    private fun collect(
        file: PsiFile,
        visited: MutableSet<VirtualFile>,
        depth: Int,
        into: MutableList<GalenObjectDefinition>,
    ) {
        if (depth > MAX_IMPORT_DEPTH) return
        val virtual = file.virtualFile ?: file.originalFile.virtualFile
        if (virtual != null && !visited.add(virtual)) return

        into += PsiTreeUtil.findChildrenOfType(file, GalenObjectDefinition::class.java)
        for (imported in importedFiles(file)) collect(imported, visited, depth + 1, into)
    }

    private fun findInScope(
        name: String,
        file: PsiFile,
        visited: MutableSet<VirtualFile>,
        depth: Int,
    ): GalenObjectDefinition? {
        if (depth > MAX_IMPORT_DEPTH) return null
        val virtual = file.virtualFile ?: file.originalFile.virtualFile
        if (virtual != null && !visited.add(virtual)) return null

        matchIn(PsiTreeUtil.findChildrenOfType(file, GalenObjectDefinition::class.java), name)
            ?.let { return it }

        for (imported in importedFiles(file)) {
            findInScope(name, imported, visited, depth + 1)?.let { return it }
        }
        return null
    }

    /**
     * Exact match first, then wildcard families: a reference to `menu_item-3` is satisfied by a
     * `menu_item-*` declaration, which is how Galen names the objects a single locator finds.
     */
    private fun matchIn(
        declarations: Collection<GalenObjectDefinition>,
        name: String,
    ): GalenObjectDefinition? {
        declarations.firstOrNull { !it.isPattern && it.qualifiedName == name }?.let { return it }
        return declarations.firstOrNull { it.isPattern && matchesPattern(it.qualifiedName, name) }
    }

    /** Galen's object matching: `*` matches any run of characters, `#` matches digits only. */
    fun matchesPattern(pattern: String?, name: String): Boolean {
        if (pattern == null) return false
        val regex = StringBuilder("^")
        for (c in pattern) {
            when (c) {
                '*' -> regex.append(".*")
                '#' -> regex.append("\\d+")
                else -> regex.append(Regex.escape(c.toString()))
            }
        }
        regex.append('$')
        return runCatching { Regex(regex.toString()).matches(name) }.getOrDefault(false)
    }

    private fun importedFiles(file: PsiFile): List<PsiFile> {
        val directory = (file.virtualFile ?: file.originalFile.virtualFile)?.parent ?: return emptyList()
        val manager = PsiManager.getInstance(file.project)

        return GalenImportUtil.importPathsOf(file).mapNotNull { path ->
            val target = directory.findFileByRelativePath(path) ?: return@mapNotNull null
            manager.findFile(target)?.takeIf { it is GalenFile }
        }
    }

    /**
     * A file that declares [name] but is not reachable through imports. Reported as a hint rather
     * than resolved, because Galen would not see it either.
     */
    private fun declaredElsewhere(
        name: String,
        from: PsiFile,
        alreadySearched: Set<VirtualFile>,
    ): String? {
        val project = from.project
        val candidates = GalenObjectIndex.filesDeclaring(name, project)
            .filterNot { it in alreadySearched }
        if (candidates.isNotEmpty()) return candidates.first().name

        // A concrete name may be covered by a wildcard family in an unimported file.
        val manager = PsiManager.getInstance(project)
        for (file in GalenObjectIndex.filesWithPatterns(project)) {
            if (file in alreadySearched) continue
            val psi = manager.findFile(file) as? GalenFile ?: continue
            val match = PsiTreeUtil.findChildrenOfType(psi, GalenObjectDefinition::class.java)
                .firstOrNull { it.isPattern && matchesPattern(it.qualifiedName, name) }
            if (match != null) return file.name
        }
        return null
    }
}
