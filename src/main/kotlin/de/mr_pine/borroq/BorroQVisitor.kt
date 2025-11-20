package de.mr_pine.borroq

import com.sun.source.tree.ClassTree
import com.sun.source.tree.MethodInvocationTree
import com.sun.source.tree.MethodTree
import com.sun.source.tree.Tree
import org.checkerframework.framework.source.SourceVisitor

class BorroQVisitor(val checker: BorroQChecker, val strictness: Strictness, val typeQuery: TypeQuery = TypeQuery(checker)) : SourceVisitor<Unit, Unit>(checker) {

    fun unhandled(node: Tree) {
        if (strictness == Strictness.STRICT) {
            checker.reportError(node, Messages.UNKNOWN_TREE_ENCOUNTERED)
        } else {
            checker.reportWarning(node, Messages.UNKNOWN_TREE_ENCOUNTERED)
        }
    }

    override fun visitClass(classTree: ClassTree?, p: Unit?) {
        return super.visitClass(classTree, p)
    }

    override fun visitMethod(tree: MethodTree?, p: Unit?) {
        return super.visitMethod(tree, p)
    }

    override fun visitMethodInvocation(node: MethodInvocationTree?, p: Unit?) {
    }
}