package com.galenlinter.navigation

import com.galenlinter.lang.GalenTypes
import com.galenlinter.psi.GalenFile
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.ElementColorProvider
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import java.awt.Color

/**
 * A colour swatch in the gutter next to each `color-scheme` value, editable with the colour picker.
 *
 * `color-scheme 10% white, < 30% #f845b7` is otherwise a wall of hex, and the whole point of the
 * spec is what those colours look like.
 */
class GalenColorProvider : ElementColorProvider {

    override fun getColorFrom(element: PsiElement): Color? {
        if (element.containingFile !is GalenFile) return null
        if (element.node?.elementType != GalenTypes.COLOR_VALUE) return null
        return parse(element.text)
    }

    override fun setColorTo(element: PsiElement, color: Color) {
        if (element.node?.elementType != GalenTypes.COLOR_VALUE) return
        val file = element.containingFile ?: return
        val documentManager = PsiDocumentManager.getInstance(element.project)
        val document = documentManager.getDocument(file) ?: return

        val hex = "#%02x%02x%02x".format(color.red, color.green, color.blue)
        val range = element.textRange

        WriteCommandAction.runWriteCommandAction(element.project, "Change Colour", null, {
            document.replaceString(range.startOffset, range.endOffset, hex)
            documentManager.commitDocument(document)
        }, file)
    }

    /**
     * Only a single literal colour gets a swatch.
     *
     * A gradient such as `#000-#555-#955` is several colours in one token and has no single value
     * to show or to set, so it is left alone rather than misrepresented by its first stop.
     */
    private fun parse(text: String): Color? {
        if (!text.startsWith("#")) return NAMED[text.lowercase()]
        val hex = text.substring(1)
        if (hex.any { it !in "0123456789abcdefABCDEF" }) return null

        return when (hex.length) {
            3 -> Color(
                hex[0].digitToInt(16) * 17,
                hex[1].digitToInt(16) * 17,
                hex[2].digitToInt(16) * 17,
            )
            6 -> Color(
                hex.substring(0, 2).toInt(16),
                hex.substring(2, 4).toInt(16),
                hex.substring(4, 6).toInt(16),
            )
            else -> null
        }
    }

    private companion object {
        /** The colour names Galen's own documentation uses in `color-scheme` examples. */
        val NAMED = mapOf(
            "white" to Color.WHITE,
            "black" to Color.BLACK,
            "red" to Color.RED,
            "green" to Color.GREEN,
            "blue" to Color.BLUE,
            "yellow" to Color.YELLOW,
            "cyan" to Color.CYAN,
            "magenta" to Color.MAGENTA,
            "gray" to Color.GRAY,
            "grey" to Color.GRAY,
            "orange" to Color.ORANGE,
            "pink" to Color.PINK,
        )
    }
}
