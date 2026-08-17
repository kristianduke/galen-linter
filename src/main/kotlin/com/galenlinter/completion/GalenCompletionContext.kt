package com.galenlinter.completion

/**
 * What the caret is positioned to complete.
 *
 * Derived from the **text of the current line up to the caret**, plus a backwards indentation scan
 * for the enclosing block, rather than from the PSI. During completion IntelliJ inserts a dummy
 * identifier at the caret, which in a line-oriented grammar can reshape the parse of the very line
 * being analysed; the raw prefix is stable and is all this decision needs.
 */
internal data class GalenCompletionContext(
    /** The word being typed, used as the prefix matcher. */
    val currentWord: String,
    /** Whitespace-separated words already complete on this line, before [currentWord]. */
    val words: List<String>,
    /** The header line of the nearest enclosing block, trimmed, or null at top level. */
    val parentHeader: String?,
    val indent: Int,
) {
    val isFirstWordOnLine: Boolean get() = words.isEmpty()

    /** Inside the body of `@objects`. */
    val inObjectsBlock: Boolean get() = parentHeader?.startsWith("@objects") == true

    val inGroupsBlock: Boolean get() = parentHeader?.startsWith("@groups") == true

    val inSetBlock: Boolean get() = parentHeader?.startsWith("@set") == true

    val inScriptBlock: Boolean get() = parentHeader?.let {
        it == "@script" || it.startsWith("@script ").not() && it == "@script"
    } == true

    /** Inside the specs of an `objectName:` statement. */
    val inObjectStatement: Boolean
        get() {
            val header = parentHeader ?: return false
            return header.endsWith(":") && !header.startsWith("@")
        }

    /** The spec name on this line, when one has been typed. */
    val specName: String? get() = words.firstOrNull { it != "%" && !it.startsWith("\"") }

    /** Arguments typed after the spec name. */
    val specArguments: List<String>
        get() {
            val name = specName ?: return emptyList()
            val index = words.indexOf(name)
            return if (index < 0) emptyList() else words.drop(index + 1)
        }

    companion object {

        private const val TAB_SIZE = 4

        fun of(fileText: CharSequence, caret: Int): GalenCompletionContext {
            val safeCaret = caret.coerceIn(0, fileText.length)
            val lineStart = lineStartOf(fileText, safeCaret)
            val prefix = fileText.subSequence(lineStart, safeCaret).toString()

            val currentWord = prefix.takeLastWhile { !it.isWhitespace() }
            val completed = prefix.dropLast(currentWord.length)
            val words = completed.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

            val indent = indentWidth(prefix)

            return GalenCompletionContext(
                currentWord = currentWord,
                words = words,
                parentHeader = parentHeaderOf(fileText, lineStart, indent),
                indent = indent,
            )
        }

        private fun lineStartOf(text: CharSequence, offset: Int): Int {
            var i = offset - 1
            while (i >= 0 && text[i] != '\n') i--
            return i + 1
        }

        /**
         * The nearest preceding line with strictly smaller indentation — the header of the block
         * this line belongs to. Blank and comment lines are skipped, matching Galen.
         */
        private fun parentHeaderOf(text: CharSequence, lineStart: Int, indent: Int): String? {
            var cursor = lineStart - 1
            while (cursor > 0) {
                val start = lineStartOf(text, cursor)
                val line = text.subSequence(start, cursor).toString()
                val trimmed = line.trim()

                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    if (indentWidth(line) < indent) return trimmed
                }
                cursor = start - 1
            }
            return null
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
    }
}
