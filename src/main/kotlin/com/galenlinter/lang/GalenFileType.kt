package com.galenlinter.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object GalenIcons {
    @JvmField
    val FILE: Icon = IconLoader.getIcon("/icons/galen.svg", GalenIcons::class.java)
}

object GalenFileType : LanguageFileType(GalenLanguage) {
    override fun getName(): String = "Galen Spec"

    override fun getDescription(): String = "Galen Framework page spec"

    override fun getDefaultExtension(): String = "gspec"

    override fun getIcon(): Icon = GalenIcons.FILE
}
