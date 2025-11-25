package de.mr_pine.borroq.analysis.exceptions

import com.sun.source.tree.Tree
import org.checkerframework.framework.source.SourceChecker

open class BorroQException(private val messageKey: String, vararg val arguments: String): Exception() {
    context(checker: SourceChecker, tree: Tree)
    fun report() {
        checker.reportError(tree, messageKey, *arguments)
    }
}