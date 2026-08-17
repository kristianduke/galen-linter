package com.galenlinter.resolve

import com.galenlinter.index.GalenObjectIndex
import com.galenlinter.lang.GalenTypes
import com.galenlinter.psi.GalenFilePathRef
import com.galenlinter.psi.GalenGroupRef
import com.galenlinter.psi.GalenObjectNameRef
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException

/**
 * Ctrl+click from an object name in a spec to its `@objects` declaration.
 *
 * `soft = true` throughout: the platform paints an unresolved *hard* reference with an error
 * highlight of its own, which would double up with GL201 and, worse, fire on the `${...}` names
 * that must stay silent. Reporting is left entirely to the inspection.
 */
class GalenObjectReference(element: GalenObjectNameRef) :
    PsiReferenceBase<GalenObjectNameRef>(element, TextRange(0, element.textLength), true) {

    override fun resolve(): PsiElement? =
        when (val resolution = GalenObjectResolver.resolve(element.text, element.containingFile)) {
            is Resolution.Found -> resolution.declaration
            else -> null
        }

    override fun getVariants(): Array<Any> {
        val names: List<Any> = GalenObjectResolver.declarationsInScope(element.containingFile)
            .mapNotNull { it.qualifiedName }
            .plus(GalenTypes.SPECIAL_OBJECTS)
            .distinct()
        return names.toTypedArray()
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val node = element.node.firstChildNode
            ?: throw IncorrectOperationException("Cannot rename this reference")
        // Only a single-token name can be rewritten safely. A composite name such as
        // `menu_item-${index}` is part expression, and replacing it wholesale would destroy the
        // expression.
        if (node.treeNext != null) {
            throw IncorrectOperationException(
                "'${element.text}' is built from an expression and cannot be renamed automatically.",
            )
        }
        return super.handleElementRename(newElementName)
    }
}

/** Ctrl+click from `&groupName` to its `@groups` declaration. */
class GalenGroupReference(element: GalenGroupRef) :
    PsiReferenceBase<GalenGroupRef>(element, TextRange(1, element.textLength), true) {

    override fun resolve(): PsiElement? {
        val name = element.groupName
        if (name.isEmpty() || name.contains("\${")) return null
        return GalenGroupUtil.findDeclaration(name, element.containingFile)
    }

    override fun getVariants(): Array<Any> {
        val names: List<Any> = GalenGroupUtil.namesInScope(element.containingFile)
        return names.toTypedArray()
    }
}

/**
 * File references for `@import`, `@script`, `component`, `image file` and `filter mask`.
 *
 * Paths resolve against the containing file's directory, matching `ImportProcessor`, which builds
 * its context path from `GalenUtils.getParentForFile`.
 */
class GalenFileReferenceSet(element: GalenFilePathRef) :
    FileReferenceSet(element.text, element, 0, null, true) {

    override fun isEndingSlashNotAllowed(): Boolean = true

    /**
     * Soft, so the platform does not add its own error highlight for an unresolved path. Galen
     * accepts classpath resources that are invisible here, so GL501 reports these as warnings
     * instead — one diagnostic, at the right confidence.
     */
    override fun isSoft(): Boolean = true

    override fun computeDefaultContexts(): Collection<PsiFileSystemItem> {
        // Deliberately only the containing directory. Galen also accepts classpath resources, but
        // those are invisible here, which is exactly why a missing file is reported as a warning
        // rather than an error.
        val directory = element.containingFile.originalFile.containingDirectory ?: return emptyList()
        return listOf(directory)
    }
}

object GalenGroupUtil {

    /**
     * A group is declared either by a `@groups` line or inline via `@grouped(...)`, so both are
     * searched. The declaration returned is the element bearing the name, which is what
     * ctrl+click should land on.
     */
    fun findDeclaration(name: String, file: PsiFile): PsiElement? {
        for (definition in groupDefinitions(file)) {
            if (namesIn(definition).contains(name)) return definition
        }
        return null
    }

    fun namesInScope(file: PsiFile): List<String> =
        groupDefinitions(file).flatMap { namesIn(it) }.distinct()

    private fun groupDefinitions(file: PsiFile): List<PsiElement> =
        PsiTreeUtil.findChildrenOfAnyType(file, PsiElement::class.java)
            .filter { it.node?.elementType == GalenTypes.GROUP_DEF }

    /**
     * The group names a `@groups` line declares: either a bare leading name, or a parenthesised
     * list assigning the same objects to several groups at once.
     */
    private fun namesIn(definition: PsiElement): List<String> {
        val text = definition.text.trim()
        if (text.startsWith("(")) {
            val close = text.indexOf(')')
            if (close < 0) return emptyList()
            return text.substring(1, close).split(',').map { it.trim() }.filter { it.isNotEmpty() }
        }
        val first = text.takeWhile { !it.isWhitespace() }
        return if (first.isEmpty()) emptyList() else listOf(first)
    }
}

/** Project-wide object names, for completion in 0.4.0 and for the "declared elsewhere" hint. */
object GalenIndexQueries {
    fun allObjectNames(file: PsiFile): Collection<String> =
        GalenObjectIndex.allNames(file.project)
}
