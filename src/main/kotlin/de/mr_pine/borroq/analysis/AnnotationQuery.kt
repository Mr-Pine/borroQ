package de.mr_pine.borroq.analysis

import com.sun.source.tree.Tree
import com.sun.source.tree.VariableTree
import de.mr_pine.borroq.BorroQChecker
import org.checkerframework.javacutil.TreeUtils
import javax.lang.model.element.AnnotationMirror

class AnnotationQuery(val checker: BorroQChecker) {
    fun getDeclaredVariableAnnotations(tree: VariableTree): List<AnnotationMirror> {
        return TreeUtils.elementFromDeclaration(tree).asType().annotationMirrors
    }

    fun getAssignmentLeftSideAnnotations(tree: Tree) = when (tree) {
        is VariableTree -> getDeclaredVariableAnnotations(tree)
        else -> null
    }

    fun getTypeMutability(tree: Tree): List<AnnotationMirror> {
        return TreeUtils.typeOf(tree).annotationMirrors
    }
}