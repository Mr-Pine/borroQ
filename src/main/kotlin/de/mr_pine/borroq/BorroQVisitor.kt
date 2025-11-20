package de.mr_pine.borroq

import com.sun.source.tree.ClassTree
import com.sun.source.tree.MethodInvocationTree
import com.sun.source.tree.MethodTree
import org.checkerframework.framework.source.SourceVisitor

class BorroQVisitor(val checker: BorroQChecker, val strictness: Strictness, val typeQuery: TypeQuery = TypeQuery(checker)) : SourceVisitor<Unit, Unit>(checker) {


    override fun visitClass(classTree: ClassTree?, p: Unit?) {
        return super.visitClass(classTree, p)
    }

    override fun visitMethod(tree: MethodTree?, p: Unit?) {
        return super.visitMethod(tree, p)
    }

    override fun visitMethodInvocation(node: MethodInvocationTree?, p: Unit?) {
    }
}