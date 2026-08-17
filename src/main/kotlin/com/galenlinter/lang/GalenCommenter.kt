package com.galenlinter.lang

import com.intellij.lang.Commenter

/**
 * Galen has line comments only, and a comment is recognised solely by `#` being the first
 * non-blank character of the line — there is no block-comment or end-of-line-comment form.
 */
class GalenCommenter : Commenter {
    override fun getLineCommentPrefix(): String = "#"

    override fun getBlockCommentPrefix(): String? = null

    override fun getBlockCommentSuffix(): String? = null

    override fun getCommentedBlockCommentPrefix(): String? = null

    override fun getCommentedBlockCommentSuffix(): String? = null
}
