package com.galenlinter.index

/**
 * Text-level scanner for the declarations in a spec file.
 *
 * Used by the file-based index, which must not build PSI: indexing runs over raw file content for
 * files that are not open, so it has to work from characters alone. In-file and cross-file
 * *resolution* still goes through PSI, because navigation needs a real element to jump to — the
 * index only answers "which files declare this name".
 *
 * The indentation rules are Galen's own (`IndentationStructureParser`): a tab counts as four
 * columns, and blank and `#` lines are skipped before any structural decision.
 */
object GalenDeclarationScanner {

    private const val TAB_SIZE = 4

    data class Declaration(val qualifiedName: String, val offset: Int, val isPattern: Boolean)

    /** Every object declared in the file, with nesting flattened to dotted names. */
    fun scanObjects(text: CharSequence): List<Declaration> {
        val result = mutableListOf<Declaration>()

        var objectsIndent: Int? = null
        // (indentation, name) for each open nesting level inside the current @objects block.
        val stack = ArrayDeque<Pair<Int, String>>()

        forEachSignificantLine(text) { lineStart, line ->
            val indent = indentWidth(line)
            val trimmed = line.trim()

            val blockIndent = objectsIndent
            if (blockIndent != null && indent <= blockIndent) {
                objectsIndent = null
                stack.clear()
            }

            if (trimmed == "@objects" || trimmed.startsWith("@objects ")) {
                objectsIndent = indent
                stack.clear()
                return@forEachSignificantLine
            }

            if (objectsIndent == null) return@forEachSignificantLine

            val name = declaredNameOf(trimmed) ?: return@forEachSignificantLine

            while (stack.isNotEmpty() && stack.last().first >= indent) stack.removeLast()

            val qualified = if (stack.isEmpty()) name else {
                stack.joinToString(".") { it.second } + "." + name
            }
            result += Declaration(
                qualifiedName = qualified,
                offset = lineStart + line.indexOf(name.first()),
                isPattern = name.contains('*') || name.contains('#'),
            )
            stack.addLast(indent to name)
        }

        return result
    }

    /** Paths named by `@import` statements, in declaration order. */
    fun scanImports(text: CharSequence): List<String> {
        val result = mutableListOf<String>()
        forEachSignificantLine(text) { _, line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("@import")) {
                val path = trimmed.removePrefix("@import").trim()
                if (path.isNotEmpty()) result += path
            }
        }
        return result
    }

    /**
     * The object name at the start of an `@objects` entry.
     *
     * Everything after the name is the correction, the optional `@grouped(...)`, an optional
     * locator type and the locator, none of which this scanner needs.
     */
    private fun declaredNameOf(trimmed: String): String? {
        if (trimmed.isEmpty() || trimmed.startsWith('#') || trimmed.startsWith('@')) return null
        val name = trimmed.takeWhile { !it.isWhitespace() }
        if (name.isEmpty()) return null
        // A definition always has a locator after the name; a bare word is something else.
        if (name.length == trimmed.length) return null
        return name
    }

    private fun indentWidth(line: String): Int {
        var width = 0
        for (c in line) {
            when (c) {
                ' ' -> width++
                '\t' -> width += TAB_SIZE
                else -> return width
            }
        }
        return width
    }

    private inline fun forEachSignificantLine(text: CharSequence, action: (Int, String) -> Unit) {
        var start = 0
        var i = 0
        while (i <= text.length) {
            if (i == text.length || text[i] == '\n') {
                var end = i
                if (end > start && text[end - 1] == '\r') end--
                val line = text.subSequence(start, end).toString()
                if (line.isNotBlank() && !line.trimStart().startsWith("#")) action(start, line)
                start = i + 1
                if (i == text.length) break
            }
            i++
        }
    }
}
