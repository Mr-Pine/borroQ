package de.mr_pine.borroq.types

import de.mr_pine.borroq.analysis.BorroQStore
import de.mr_pine.borroq.analysis.exceptions.ConflictingPathRestrictionsException
import de.mr_pine.borroq.analysis.transfer.BorroQTransfer.Companion.ThisId
import org.checkerframework.dataflow.cfg.node.*
import org.checkerframework.dataflow.expression.JavaExpression
import org.checkerframework.dataflow.expression.LocalVariable
import javax.lang.model.element.VariableElement

sealed interface PathRoot {
    data object ThisPathRoot : PathRoot
    data class StaticPathRoot(val className: ClassNameNode) : PathRoot

    @JvmInline
    value class IdPathRoot(val id: Id) : PathRoot

    context(store: BorroQStore)
    fun toId() = when (this) {
        is ThisPathRoot -> ThisId
        is StaticPathRoot -> throw IllegalStateException("Static path root cannot be converted to id")
        is IdPathRoot -> this.id
    }
}

@JvmInline
value class PathTail(val fields: List<VariableElement>) {
    constructor(vararg fields: VariableElement) : this(fields.toList())

    fun isPrefixOf(other: PathTail) = fields.size <= other.fields.size && fields.zip(other.fields)
        .all { (a, b) -> a == b }

    fun with(element: VariableElement) = PathTail(fields + element)

    companion object {
        fun checkForConflicts(paths: List<PathTail>) {
            for ((i, path) in paths.withIndex()) {
                val conflicting = paths.filterIndexed { index, _ -> index != i }.filter { path.isPrefixOf(it) }
                if (conflicting.isNotEmpty()) throw ConflictingPathRestrictionsException(path, conflicting.first())
            }
        }
    }
}

data class Path(val root: PathRoot, val tail: PathTail) {
    constructor(root: PathRoot) : this(root, PathTail(emptyList()))

    fun with(field: VariableElement) = Path(root, PathTail(tail.fields + field))
    fun with(tail: PathTail) = Path(root, PathTail(this.tail.fields + tail.fields))

    val isStatic: Boolean get() = root is PathRoot.StaticPathRoot && tail.fields.size == 1 // TODO: Remove special case

    companion object {
        fun fromNode(node: Node): Path = when (node) {
            is LocalVariableNode -> {
                val variable = JavaExpression.fromNode(node) as LocalVariable
                TODO()
                //Path(PathRoot.LocalVariableRoot(variable))
            }

            is ThisNode -> Path(PathRoot.ThisPathRoot)
            is FieldAccessNode -> {
                val basePath = fromNode(node.receiver)
                basePath.with(node.element)
            }

            is ClassNameNode -> TODO() // Path(PathRoot.StaticPathRoot)

            else -> throw IllegalStateException("Unexpected node type ${node.javaClass}")
        }
    }
}
