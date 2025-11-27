package de.mr_pine.borroq.analysis.stub

import org.checkerframework.com.github.javaparser.ast.Node
import org.checkerframework.com.github.javaparser.ast.body.ConstructorDeclaration
import org.checkerframework.com.github.javaparser.ast.body.Parameter
import org.checkerframework.com.github.javaparser.ast.type.ArrayType
import org.checkerframework.com.github.javaparser.ast.type.ClassOrInterfaceType
import org.checkerframework.com.github.javaparser.ast.type.PrimitiveType
import org.checkerframework.com.github.javaparser.ast.visitor.GenericVisitorAdapter

class StubElementPrinter : GenericVisitorAdapter<String, Unit>() {
    override fun visit(
        constructor: ConstructorDeclaration, arg: Unit
    ): String {
        val paramString = constructor.parameters.joinToString(",") {
            it.accept(this, Unit)
        }
        return "<init>($paramString)"
    }

    override fun visit(
        param: Parameter, arg: Unit
    ): String {
        val base = param.type.accept(this, Unit)
        return if (param.isVarArgs) "$base[]" else base
    }

    override fun visit(
        type: ClassOrInterfaceType,
        arg: Unit
    ): String? {
        return type.name.asString()
    }

    override fun visit(
        arr: ArrayType,
        arg: Unit
    ): String {
        return "${arr.componentType.accept(this, Unit)}[]"
    }

    override fun visit(
        type: PrimitiveType,
        arg: Unit
    ): String {
        return type.type.name.lowercase()
    }

    companion object {
        fun print(node: Node): String? {
            val printer = StubElementPrinter()
            return node.accept(printer, Unit)
        }
    }
}