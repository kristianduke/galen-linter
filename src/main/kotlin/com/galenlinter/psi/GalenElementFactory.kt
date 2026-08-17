package com.galenlinter.psi

import com.galenlinter.lang.GalenFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil

/**
 * Builds PSI by parsing a throwaway spec file.
 *
 * Rename needs a replacement name node, and constructing AST by hand is both verbose and easy to
 * get subtly wrong. Parsing a minimal well-formed document instead means the factory can never
 * produce a shape the parser would not.
 */
object GalenElementFactory {

    /** A single object-name element, or null when [name] would not parse as one. */
    fun createObjectName(project: Project, name: String): PsiElement? {
        if (name.isBlank() || name.any { it.isWhitespace() }) return null

        val file = PsiFileFactory.getInstance(project)
            .createFileFromText("dummy.gspec", GalenFileType, "@objects\n    $name  #locator\n")

        val definition = PsiTreeUtil.findChildOfType(file, GalenObjectDefinition::class.java)
            ?: return null
        val identifier = definition.nameIdentifier ?: return null

        // Guard against a name that lexes into several tokens (a space, a colon, a `${`): the
        // factory must not silently produce a partial rename.
        return if (identifier.text == name) identifier else null
    }
}
