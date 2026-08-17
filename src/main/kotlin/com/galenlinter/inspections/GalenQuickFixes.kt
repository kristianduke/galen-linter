package com.galenlinter.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager

/**
 * Replaces a single-token element with a suggested word.
 *
 * Every "did you mean" diagnostic already computes its suggestion in order to word the message, so
 * turning that into a fix is nearly free — and this is the shape most Galen mistakes take: one
 * misspelled keyword in an otherwise correct line.
 */
class GalenReplaceWordFix(private val replacement: String) : LocalQuickFix {

    override fun getName(): String = "Change to '$replacement'"

    override fun getFamilyName(): String = "Change to the suggested keyword"

    /**
     * Edits the document rather than the AST.
     *
     * Replacing the leaf directly with `LeafElement.replaceWithText` looks tidier but corrupts the
     * tree: correcting `widht` to `width` turns an unrecognised spec into a recognised one, which
     * changes how the *rest of the line* parses (its arguments become a RANGE). An incremental
     * reparse of just that leaf cannot know this, so the PSI ends up disagreeing with a fresh parse.
     * A document edit reparses the line properly.
     */
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement ?: return
        // Never rewrite a name that embeds an expression — the expression would be destroyed.
        if (element.text.contains("\${")) return

        val file = element.containingFile ?: return
        val documentManager = PsiDocumentManager.getInstance(project)
        val document = documentManager.getDocument(file) ?: return

        val range = element.textRange
        document.replaceString(range.startOffset, range.endOffset, replacement)
        documentManager.commitDocument(document)
    }
}

/**
 * Adds the `@import` that brings an out-of-scope object into scope.
 *
 * The most valuable fix in the set: GL201 already knows which file declares the name, and a missing
 * `@import` is nearly always the actual mistake rather than a typo.
 */
class GalenAddImportFix(private val path: String) : LocalQuickFix {

    override fun getName(): String = "Add '@import $path'"

    override fun getFamilyName(): String = "Add a missing @import"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val file = descriptor.psiElement?.containingFile ?: return
        val documentManager = PsiDocumentManager.getInstance(project)
        val document = documentManager.getDocument(file) ?: return

        val text = document.charsSequence
        if (Regex("(?m)^\\s*@import\\s+${Regex.escape(path)}\\s*$").containsMatchIn(text)) return

        // Insert after any existing @import so the imports stay together, otherwise after the
        // leading comment block so a file header is not split.
        val insertAt = insertionOffset(text)
        document.insertString(insertAt, "@import $path\n")
        documentManager.commitDocument(document)
    }

    private fun insertionOffset(text: CharSequence): Int {
        var offset = 0
        var candidate = 0
        val lines = text.split('\n')
        for (line in lines) {
            val trimmed = line.trim()
            val lineEnd = offset + line.length + 1
            when {
                trimmed.startsWith("@import") || trimmed.startsWith("@lib") -> candidate = lineEnd
                trimmed.startsWith("#") || trimmed.isEmpty() ->
                    if (candidate == 0) candidate = lineEnd
                else -> return candidate.coerceAtMost(text.length)
            }
            offset = lineEnd
        }
        return candidate.coerceAtMost(text.length)
    }
}

/** Rewrites a range of the file — used by the whitespace rules, which work on raw text. */
class GalenReplaceRangeFix(
    private val replacement: String,
    private val label: String,
) : LocalQuickFix {

    override fun getFamilyName(): String = label

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement ?: return
        val file = element.containingFile ?: return
        val documentManager = PsiDocumentManager.getInstance(project)
        val document = documentManager.getDocument(file) ?: return

        val relative = descriptor.textRangeInElement ?: return
        val start = element.textRange.startOffset + relative.startOffset
        val end = element.textRange.startOffset + relative.endOffset
        if (start < 0 || end > document.textLength || start > end) return

        document.replaceString(start, end, replacement)
        documentManager.commitDocument(document)
    }
}

/** Appends the missing final newline. */
class GalenAddFinalNewlineFix : LocalQuickFix {

    override fun getFamilyName(): String = "Add a final newline"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val file = descriptor.psiElement?.containingFile ?: return
        val documentManager = PsiDocumentManager.getInstance(project)
        val document = documentManager.getDocument(file) ?: return
        if (document.textLength > 0 && document.charsSequence.last() == '\n') return
        document.insertString(document.textLength, "\n")
        documentManager.commitDocument(document)
    }
}
