package com.galenlinter.inspections

import com.galenlinter.lang.GalenTypes
import com.galenlinter.psi.GalenFile
import com.galenlinter.psi.GalenFilePathRef
import com.galenlinter.psi.GalenGroupRef
import com.galenlinter.psi.GalenObjectNameRef
import com.galenlinter.resolve.GalenObjectResolver
import com.galenlinter.resolve.Resolution
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference

/**
 * GL201 / GL202 — references that do not resolve to a declaration in scope.
 *
 * Reported as **warnings**, not errors, and the reason matters. Resolution can legitimately come up
 * empty for reasons that are not mistakes:
 *
 *  - an `@import` path that resolves to a classpath resource, which is invisible from the IDE;
 *  - objects contributed at run time by a JavaScript rule via `addObjectSpecs`;
 *  - a name assembled from an expression, though those are filtered out before reaching here.
 *
 * An error badge on a working file is far more damaging than a missed warning, so the severity
 * reflects the confidence available.
 */
class GalenUnresolvedReferenceInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element.containingFile !is GalenFile) return
                when (element) {
                    is GalenObjectNameRef -> checkObject(element, holder)
                    is GalenGroupRef -> checkGroup(element, holder)
                }
            }
        }

    private fun checkObject(element: GalenObjectNameRef, holder: ProblemsHolder) {
        val name = element.text
        if (name.isEmpty()) return

        when (val resolution = GalenObjectResolver.resolve(name, element.containingFile)) {
            is Resolution.Found, Resolution.Builtin, Resolution.Dynamic -> return
            is Resolution.NotFound -> {
                // The most useful thing to say is not "unknown" but "you forgot to import it".
                val message = if (resolution.declaredElsewhere != null) {
                    "GL201: Object '$name' is declared in ${resolution.declaredElsewhere} but that " +
                        "file is not imported here. Add '@import ${resolution.declaredElsewhere}'."
                } else {
                    "GL201: Object '$name' is not declared in this file or anything it imports."
                }
                holder.registerProblem(element, message)
            }
        }
    }

    private fun checkGroup(element: GalenGroupRef, holder: ProblemsHolder) {
        val name = element.groupName
        if (name.isEmpty() || name.contains("\${")) return
        if (element.reference?.resolve() != null) return
        holder.registerProblem(
            element,
            "GL202: Object group '$name' is not declared in a '@groups' block or by '@grouped(...)'.",
        )
    }
}

/**
 * GL501 — a referenced file that cannot be found on disk.
 *
 * A warning rather than an error because `ImportProcessor` resolves through
 * `GalenUtils.findFileOrResourceAsStream`, so a path missing from the filesystem may still be a
 * perfectly good classpath resource.
 */
class GalenMissingFileInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element.containingFile !is GalenFile) return
                if (element !is GalenFilePathRef) return

                // `@lib` names a library bundled inside the Galen jar
                // (/spec-libs/<name>/<name>.gspec), not a path relative to this file, so it can
                // never be found on disk and must not be reported.
                if (element.parent?.node?.elementType == GalenTypes.LIB_STATEMENT) return

                val path = element.text
                if (path.isEmpty() || path.contains("\${")) return

                val references = element.references.filterIsInstance<FileReference>()
                if (references.isEmpty()) return
                // Only the last reference in the chain names the file itself.
                val last = references.last()
                if (last.resolve() != null) return

                holder.registerProblem(
                    element,
                    "GL501: Cannot find '$path' relative to this file. " +
                        "Galen also accepts classpath resources, so this may still be valid at run time.",
                )
            }
        }
}
