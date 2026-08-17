package com.galenlinter.documentation

import com.galenlinter.lang.GalenTypes
import com.galenlinter.psi.GalenFile
import com.galenlinter.psi.GalenObjectDefinition
import com.galenlinter.psi.GalenObjectNameRef
import com.galenlinter.resolve.GalenObjectResolver
import com.galenlinter.resolve.Resolution
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

/**
 * Hover and Quick Documentation for Galen.
 *
 * Two kinds of target:
 *  - **keywords** — spec names, statements, sides, matchers and the rest, answered from
 *    [GalenDocs];
 *  - **objects** — hovering a name shows where it is declared and with which locator, which is the
 *    question actually being asked when reading someone else's spec.
 *
 * Most targets are not references, so [getCustomDocumentationElement] is what makes hovering a bare
 * keyword work at all: without it the platform only offers documentation for resolved references.
 */
class GalenDocumentationProvider : AbstractDocumentationProvider() {

    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int,
    ): PsiElement? {
        if (file !is GalenFile) return null
        val element = contextElement ?: return null
        return documentableAncestorOf(element)
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val target = element ?: return null
        if (target.containingFile !is GalenFile) return null

        if (target is GalenObjectDefinition) return renderObject(target)

        val doc = docFor(target) ?: return null
        return render(doc)
    }

    /** The one-line summary shown in the completion popup and on Ctrl+hover. */
    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        val target = element ?: return null
        if (target is GalenObjectDefinition) {
            val name = target.qualifiedName ?: return null
            val locator = target.locatorText() ?: return name
            return "$name &nbsp;<i>${target.locatorType()}</i> $locator"
        }
        return docFor(target)?.summary
    }

    // -----------------------------------------------------------------------

    /**
     * Walks out from the token under the caret to the nearest element that has documentation.
     * Stops at the spec line: going further would answer a question that was not asked.
     */
    private fun documentableAncestorOf(element: PsiElement): PsiElement? {
        var current: PsiElement? = element
        var depth = 0
        while (current != null && depth < 6) {
            if (current.node?.elementType == GalenTypes.AT_KEYWORD) return current
            if (docFor(current) != null) return current
            if (current is GalenObjectNameRef) return resolveObject(current) ?: current
            if (current is GalenObjectDefinition) return current
            current = current.parent
            depth++
        }
        return null
    }

    private fun resolveObject(reference: GalenObjectNameRef): PsiElement? =
        when (val resolution = GalenObjectResolver.resolve(reference.text, reference.containingFile)) {
            is Resolution.Found -> resolution.declaration
            else -> null
        }

    private fun docFor(element: PsiElement): GalenDoc? {
        val text = element.text ?: return null
        return when (element.node?.elementType) {
            GalenTypes.SPEC_NAME -> GalenDocs.spec(text)
            GalenTypes.AT_KEYWORD -> GalenDocs.statement(text)
            GalenTypes.SIDE,
            GalenTypes.MATCHER,
            GalenTypes.TEXT_OPERATION,
            GalenTypes.ALIGN_DIRECTION,
            GalenTypes.ALIGN_EDGE,
            GalenTypes.CENTERED_DIRECTION,
            GalenTypes.CENTERED_RELATION,
            GalenTypes.COUNT_FILTER,
            GalenTypes.MODIFIER,
            GalenTypes.LOCATOR_TYPE,
            GalenTypes.UNIT,
            GalenTypes.RANGE_KEYWORD,
            GalenTypes.PROPERTY_NAME,
            -> GalenDocs.keyword(text)

            // A special object is written like any other name, so it is answered from the keyword
            // table rather than by resolving a declaration it does not have.
            GalenTypes.OBJECT_NAME_REF -> GalenDocs.keyword(text)

            else -> null
        }
    }

    private fun renderObject(definition: GalenObjectDefinition): String {
        val name = definition.qualifiedName ?: return ""
        val builder = StringBuilder()

        builder.append(DocumentationMarkup.DEFINITION_START)
        builder.append(escape(name))
        if (definition.isPattern) builder.append(" &nbsp;<i>object family</i>")
        builder.append(DocumentationMarkup.DEFINITION_END)

        builder.append(DocumentationMarkup.CONTENT_START)
        builder.append(
            if (definition.isPattern) {
                "Matches many elements. Galen names them <code>${escape(name.replace("*", "1"))}</code>, " +
                    "<code>${escape(name.replace("*", "2"))}</code> and so on."
            } else {
                "A page object."
            },
        )
        builder.append(DocumentationMarkup.CONTENT_END)

        builder.append(DocumentationMarkup.SECTIONS_START)
        section(builder, "Locator", "<code>${escape(definition.locatorType())}</code> ${escape(definition.locatorText() ?: "")}")
        definition.containingFile?.name?.let { section(builder, "Declared in", escape(it)) }
        builder.append(DocumentationMarkup.SECTIONS_END)

        return builder.toString()
    }

    private fun render(doc: GalenDoc): String {
        val builder = StringBuilder()

        builder.append(DocumentationMarkup.DEFINITION_START)
        builder.append(doc.title)
        builder.append(DocumentationMarkup.DEFINITION_END)

        builder.append(DocumentationMarkup.CONTENT_START)
        builder.append(doc.summary)
        builder.append(DocumentationMarkup.CONTENT_END)

        builder.append(DocumentationMarkup.SECTIONS_START)
        doc.syntax?.let { section(builder, "Syntax", "<code>$it</code>") }
        doc.values?.let { section(builder, "Values", it) }
        doc.example?.let { section(builder, "Example", "<pre>${it.replace("\n", "<br/>")}</pre>") }
        doc.note?.let { section(builder, "Note", it) }
        builder.append(DocumentationMarkup.SECTIONS_END)

        return builder.toString()
    }

    private fun section(builder: StringBuilder, header: String, body: String) {
        builder.append(DocumentationMarkup.SECTION_HEADER_START)
        builder.append(header)
        builder.append(DocumentationMarkup.SECTION_SEPARATOR)
        builder.append(body)
        builder.append(DocumentationMarkup.SECTION_END)
    }

    private fun escape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
