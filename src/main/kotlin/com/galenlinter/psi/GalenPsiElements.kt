package com.galenlinter.psi

import com.galenlinter.lang.GalenTypes
import com.galenlinter.resolve.GalenFileReferenceSet
import com.galenlinter.resolve.GalenGroupReference
import com.galenlinter.resolve.GalenObjectReference
import com.galenlinter.resolve.GalenVariableReference
import com.galenlinter.resolve.GalenVariableUtil
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import javax.swing.Icon

/**
 * An `@objects` entry.
 *
 * Implementing [PsiNameIdentifierOwner] is what buys find-usages, rename and the structure view
 * from the platform rather than from bespoke code.
 */
class GalenObjectDefinition(node: ASTNode) : ASTWrapperPsiElement(node), PsiNameIdentifierOwner {

    override fun getNameIdentifier(): PsiElement? =
        node.findChildByType(GalenTypes.OBJECT_NAME)?.psi

    override fun getName(): String? = nameIdentifier?.text

    /**
     * Point at the name rather than at the start of the line.
     *
     * An `@objects` entry begins with its indentation, so the default offset lands on whitespace.
     * `TargetElementUtilBase` compares this offset against the caret when deciding whether the
     * caret is "on" a declaration, so without this override rename and go-to-declaration simply
     * find nothing — and navigating to a declaration would place the caret on the indent.
     */
    override fun getTextOffset(): Int = nameIdentifier?.textRange?.startOffset ?: super.getTextOffset()

    /** The dotted name Galen actually exposes, e.g. `search_panel.input` for a nested entry. */
    val qualifiedName: String?
        get() {
            val own = name ?: return null
            val prefix = ancestorNames()
            return if (prefix.isEmpty()) own else prefix.joinToString(".", postfix = ".") + own
        }

    /** True for a family declaration such as `menu_item-*`, which matches many runtime objects. */
    val isPattern: Boolean
        get() = name?.let { it.contains('*') || it.contains('#') } ?: false

    private fun ancestorNames(): List<String> {
        val names = mutableListOf<String>()
        var parent = PsiTreeUtil.getParentOfType(this, GalenObjectDefinition::class.java)
        while (parent != null) {
            parent.name?.let { names += it }
            parent = PsiTreeUtil.getParentOfType(parent, GalenObjectDefinition::class.java)
        }
        return names.reversed()
    }

    override fun setName(name: String): PsiElement {
        if (isPattern) {
            // Renaming `menu_item-*` cannot mechanically rewrite the `menu_item-3` usages it
            // matches, and renaming only the declaration would silently break the file. Refuse
            // rather than approximate.
            throw IncorrectOperationException(
                "'${this.name}' is a wildcard object family. Renaming it would leave the " +
                    "individual objects it matches pointing at nothing, so it must be renamed by hand.",
            )
        }
        val identifier = nameIdentifier ?: throw IncorrectOperationException("Object has no name")
        val replacement = GalenElementFactory.createObjectName(project, name)
            ?: throw IncorrectOperationException("'$name' is not a valid object name")
        identifier.replace(replacement)
        return this
    }

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String = qualifiedName ?: "?"
        override fun getLocationString(): String? = locatorText()
        override fun getIcon(unused: Boolean): Icon? = null
    }

    fun locatorText(): String? = node.findChildByType(GalenTypes.LOCATOR)?.text?.trim()

    fun locatorType(): String =
        node.findChildByType(GalenTypes.LOCATOR_TYPE)?.text ?: "css"
}

/** The declared name inside an [GalenObjectDefinition]. */
class GalenObjectName(node: ASTNode) : ASTWrapperPsiElement(node)

/** An object name used as a spec argument; the ctrl+click source. */
class GalenObjectNameRef(node: ASTNode) : ASTWrapperPsiElement(node) {

    override fun getReference(): PsiReference? = references.firstOrNull()

    /**
     * A name written purely as `${item}` is a *variable*, not an object name — the object it names
     * is only known at run time. Pointing ctrl+click at the loop binding or `@set` entry that
     * defines it is far more useful than an object reference that can never resolve.
     */
    override fun getReferences(): Array<PsiReference> {
        if (text.isEmpty()) return PsiReference.EMPTY_ARRAY

        if (text.startsWith("\${")) {
            val range = GalenVariableUtil.identifierRangeIn(text)
            if (range != null) return arrayOf(GalenVariableReference(this, range))
        }

        return arrayOf(GalenObjectReference(this))
    }
}

/** `&groupName`. */
class GalenGroupRef(node: ASTNode) : ASTWrapperPsiElement(node) {
    /** The name without the leading `&`. */
    val groupName: String get() = text.removePrefix("&")

    override fun getReference(): PsiReference? =
        if (groupName.isEmpty()) null else GalenGroupReference(this)

    override fun getReferences(): Array<PsiReference> =
        reference?.let { arrayOf(it) } ?: PsiReference.EMPTY_ARRAY
}

/** A path in `@import`, `@script`, `component`, `image file` or `filter mask`. */
class GalenFilePathRef(node: ASTNode) : ASTWrapperPsiElement(node) {
    override fun getReferences(): Array<PsiReference> =
        GalenFileReferenceSet(this).allReferences.map { it as PsiReference }.toTypedArray()

    override fun getReference(): PsiReference? = references.firstOrNull()
}
