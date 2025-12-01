package de.mr_pine.borroq.types

import de.mr_pine.borroq.analysis.BorroQStore
import org.checkerframework.dataflow.cfg.node.FieldAccessNode
import org.checkerframework.dataflow.cfg.node.LocalVariableNode
import org.checkerframework.dataflow.cfg.node.Node
import org.checkerframework.dataflow.cfg.node.ThisNode
import org.checkerframework.dataflow.expression.JavaExpression
import org.checkerframework.dataflow.expression.LocalVariable
import javax.lang.model.element.VariableElement

sealed interface PathRoot {
    data object ThisPathRoot : PathRoot

    @JvmInline
    value class LocalVariableRoot(val variable: LocalVariable) : PathRoot

    context(store: BorroQStore)
    fun toId() = when (this) {
        is ThisPathRoot -> (store.queryThisPermission() as IdentifiedPermission).id
        is LocalVariableRoot -> (store.queryPermission(variable) as IdentifiedPermission).id
    }
}

@JvmInline
value class PathTail(val fields: List<VariableElement>)

data class Path(val root: PathRoot, val tail: PathTail) {
    constructor(root: PathRoot) : this(root, PathTail(emptyList()))

    context(store: BorroQStore)
    fun asIdPath() = IdPath(root.toId(), tail)

    fun with(field: VariableElement) = Path(root, PathTail(tail.fields + field))
    fun with(tail: PathTail) = Path(root, PathTail(this.tail.fields + tail.fields))

    companion object {
        fun fromNode(node: Node): Path = when (node) {
            is LocalVariableNode -> {
                val variable = JavaExpression.fromNode(node) as LocalVariable
                Path(PathRoot.LocalVariableRoot(variable))
            }

            is ThisNode -> Path(PathRoot.ThisPathRoot)
            is FieldAccessNode -> {
                val basePath = fromNode(node.receiver)
                basePath.with(node.element)
            }

            else -> throw IllegalStateException("Unexpected node type ${node.javaClass}")
        }
    }
}

data class IdPath(val id: Id, val tail: PathTail) {
    constructor(id: Id) : this(id, PathTail(emptyList()))

    fun isPrefixOf(other: IdPath) =
        id == other.id && tail.fields.size <= other.tail.fields.size && tail.fields.zip(other.tail.fields)
            .all { (a, b) -> a == b }

    fun with(field: VariableElement) = IdPath(id, PathTail(tail.fields + field))
    fun with(tail: PathTail) = IdPath(id, PathTail(this.tail.fields + tail.fields))
}
